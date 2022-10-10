/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.compute.transport;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.TransportAction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.compute.Experimental;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.operator.Driver;
import org.elasticsearch.core.Releasables;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.search.SearchService;
import org.elasticsearch.search.internal.AliasFilter;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.search.internal.ShardSearchRequest;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.esql.plan.physical.LocalExecutionPlanner;
import org.elasticsearch.xpack.esql.plan.physical.PlanNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * For simplicity, we run this on a single local shard for now
 */
@Experimental
public class TransportComputeAction extends TransportAction<ComputeRequest, ComputeResponse> {

    private final IndexNameExpressionResolver indexNameExpressionResolver;
    private final SearchService searchService;
    private final ClusterService clusterService;
    private final ThreadPool threadPool;

    @Inject
    public TransportComputeAction(
        ThreadPool threadPool,
        ClusterService clusterService,
        TransportService transportService,
        SearchService searchService,
        ActionFilters actionFilters,
        IndexNameExpressionResolver indexNameExpressionResolver
    ) {
        super(ComputeAction.NAME, actionFilters, transportService.getTaskManager());
        this.indexNameExpressionResolver = indexNameExpressionResolver;
        this.searchService = searchService;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
    }

    @Override
    protected void doExecute(Task task, ComputeRequest request, ActionListener<ComputeResponse> listener) {
        try {
            asyncAction(task, request, listener);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void asyncAction(Task task, ComputeRequest request, ActionListener<ComputeResponse> listener) throws IOException {
        Index[] indices = indexNameExpressionResolver.concreteIndices(clusterService.state(), request);
        List<SearchContext> searchContexts = new ArrayList<>();
        for (Index index : indices) {
            IndexService indexService = searchService.getIndicesService().indexServiceSafe(index);
            for (IndexShard indexShard : indexService) {
                ShardSearchRequest shardSearchLocalRequest = new ShardSearchRequest(indexShard.shardId(), 0, AliasFilter.EMPTY);
                SearchContext context = searchService.createSearchContext(shardSearchLocalRequest, SearchService.NO_TIMEOUT);
                searchContexts.add(context);
            }
        }

        boolean success = false;
        try {
            searchContexts.stream().forEach(SearchContext::preProcess);

            LocalExecutionPlanner planner = new LocalExecutionPlanner(
                searchContexts.stream()
                    .map(SearchContext::getSearchExecutionContext)
                    .map(
                        sec -> new LocalExecutionPlanner.IndexReaderReference(
                            sec.getIndexReader(),
                            new ShardId(sec.index(), sec.getShardId())
                        )
                    )
                    .collect(Collectors.toList())
            );

            final List<Page> results = Collections.synchronizedList(new ArrayList<>());
            LocalExecutionPlanner.LocalExecutionPlan localExecutionPlan = planner.plan(
                new PlanNode.OutputNode(request.plan(), (l, p) -> { results.add(p); })
            );
            List<Driver> drivers = localExecutionPlan.createDrivers();
            if (drivers.isEmpty()) {
                throw new IllegalStateException("no drivers created");
            }
            logger.info("using {} drivers", drivers.size());
            Driver.start(threadPool.executor(ThreadPool.Names.SEARCH), drivers).addListener(new ActionListener<>() {
                @Override
                public void onResponse(Void unused) {
                    Releasables.close(searchContexts);
                    listener.onResponse(new ComputeResponse(new ArrayList<>(results)));
                }

                @Override
                public void onFailure(Exception e) {
                    Releasables.close(searchContexts);
                    listener.onFailure(e);
                }
            });
            success = true;
        } finally {
            if (success == false) {
                Releasables.close(searchContexts);
            }
        }
    }
}
