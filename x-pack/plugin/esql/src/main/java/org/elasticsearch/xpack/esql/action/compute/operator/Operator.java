/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.action.compute.operator;

import org.elasticsearch.action.support.ListenableActionFuture;
import org.elasticsearch.xpack.esql.action.compute.data.Page;

/**
 * Operator is low-level building block that consumes, transforms and produces data.
 * An operator can have state, and assumes single-threaded access.
 * Data is processed in smaller batches (termed {@link Page}s) that are passed to
 * (see {@link #addInput(Page)}) or retrieved from (see {@link #getOutput()} operators.
 * The component that's in charge of passing data between operators is the {@link Driver}.
 *
 * More details on how this integrates with other components can be found in the package documentation of
 * {@link org.elasticsearch.xpack.esql.action.compute}
 */
public interface Operator {

    /**
     * whether the given operator can accept more input pages
     */
    boolean needsInput();

    /**
     * adds an input page to the operator. only called when needsInput() == true and isFinished() == false
     */
    void addInput(Page page);

    /**
     * notifies the operator that it won't receive any more input pages
     */
    void finish();

    /**
     * whether the operator has finished processing all input pages and made the corresponding output pages available
     */
    boolean isFinished();

    /**
     * returns non-null if output page available. Only called when isFinished() == false
     */
    Page getOutput();

    /**
     * notifies the operator that it won't be used anymore (i.e. none of the other methods called),
     * and its resources can be cleaned up
     */
    void close();

    /**
     * An operator can be blocked on some action (e.g. waiting for some resources to become available).
     * If so, it returns a future that completes when the operator becomes unblocked.
     * If the operator is not blocked, this method returns {@link #NOT_BLOCKED} which is an already
     * completed future.
     */
    default ListenableActionFuture<Void> isBlocked() {
        return NOT_BLOCKED;
    }

    ListenableActionFuture<Void> NOT_BLOCKED = newCompletedFuture();

    static ListenableActionFuture<Void> newCompletedFuture() {
        ListenableActionFuture<Void> fut = new ListenableActionFuture<>();
        fut.onResponse(null);
        return fut;
    }
}
