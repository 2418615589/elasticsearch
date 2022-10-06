/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.action.compute.aggregation;

import org.elasticsearch.xpack.esql.action.compute.data.Block;
import org.elasticsearch.xpack.esql.action.compute.data.Page;

import java.util.function.BiFunction;

public class GroupingAggregator {
    private final GroupingAggregatorFunction aggregatorFunction;

    private final AggregatorMode mode;

    private final int intermediateChannel;

    public GroupingAggregator(
        BiFunction<AggregatorMode, Integer, GroupingAggregatorFunction> aggCreationFunc,
        AggregatorMode mode,
        int inputChannel
    ) {
        this.aggregatorFunction = aggCreationFunc.apply(mode, inputChannel);
        this.mode = mode;
        this.intermediateChannel = mode.isInputPartial() ? inputChannel : -1;
    }

    public void processPage(Block groupIdBlock, Page page) {
        if (mode.isInputPartial()) {
            aggregatorFunction.addIntermediateInput(groupIdBlock, page.getBlock(intermediateChannel));
        } else {
            aggregatorFunction.addRawInput(groupIdBlock, page);
        }
    }

    public Block evaluate() {
        if (mode.isOutputPartial()) {
            return aggregatorFunction.evaluateIntermediate();
        } else {
            return aggregatorFunction.evaluateFinal();
        }
    }
}
