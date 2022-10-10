/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.compute.operator;

import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.LongArrayBlock;
import org.elasticsearch.compute.data.Page;

import java.util.function.LongFunction;

/**
 * Streaming operator that applies a long-value transformation to a given field
 */
public class LongTransformerOperator implements Operator {

    private final int channel;
    private final LongFunction<Long> longTransformer;

    boolean finished;

    Page lastInput;

    public LongTransformerOperator(int channel, LongFunction<Long> longTransformer) {
        this.channel = channel;
        this.longTransformer = longTransformer;
    }

    @Override
    public Page getOutput() {
        if (lastInput == null) {
            return null;
        }
        Block block = lastInput.getBlock(channel);
        long[] newBlock = new long[block.getPositionCount()];
        for (int i = 0; i < block.getPositionCount(); i++) {
            newBlock[i] = longTransformer.apply(block.getLong(i));
        }
        Page lastPage = lastInput.appendBlock(new LongArrayBlock(newBlock, block.getPositionCount()));
        lastInput = null;
        return lastPage;
    }

    @Override
    public boolean isFinished() {
        return lastInput == null && finished;
    }

    @Override
    public void finish() {
        finished = true;
    }

    @Override
    public boolean needsInput() {
        return lastInput == null && finished == false;
    }

    @Override
    public void addInput(Page page) {
        lastInput = page;
    }

    @Override
    public void close() {

    }
}
