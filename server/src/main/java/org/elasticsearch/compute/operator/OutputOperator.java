/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.compute.operator;

import org.elasticsearch.compute.Experimental;
import org.elasticsearch.compute.data.Page;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static java.util.stream.Collectors.joining;

/**
 * Sink operator that calls a given listener for each page received. The listener receives both the page as well as schema information,
 * i.e. the names of the rows that are outputted.
 */
@Experimental
public class OutputOperator implements Operator {

    private final List<String> columns;
    private final BiConsumer<List<String>, Page> pageConsumer;
    private final Function<Page, Page> mapper;

    public record OutputOperatorFactory(List<String> columns, Function<Page, Page> mapper, BiConsumer<List<String>, Page> pageConsumer)
        implements
            OperatorFactory {

        @Override
        public Operator get() {
            return new OutputOperator(columns, mapper, pageConsumer);
        }

        @Override
        public String describe() {
            return "OutputOperator (columns = " + columns.stream().collect(joining(", ")) + ")";
        }
    }

    public OutputOperator(List<String> columns, Function<Page, Page> mapper, BiConsumer<List<String>, Page> pageConsumer) {
        this.columns = columns;
        this.mapper = mapper;
        this.pageConsumer = pageConsumer;
    }

    boolean finished = false;

    @Override
    public Page getOutput() {
        return null;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void finish() {
        finished = true;
    }

    @Override
    public boolean needsInput() {
        return finished == false;
    }

    @Override
    public void addInput(Page page) {
        pageConsumer.accept(columns, mapper.apply(page));
    }

    @Override
    public void close() {

    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("[");
        sb.append("columns=").append(columns).append(", ");
        sb.append("pageConsumer=").append(pageConsumer);
        sb.append("]");
        return sb.toString();
    }
}
