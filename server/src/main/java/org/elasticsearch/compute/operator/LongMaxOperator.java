/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.compute.operator;

import org.elasticsearch.compute.Experimental;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.LongArrayBlock;
import org.elasticsearch.compute.data.Page;

/**
 * Operator that computes the max value of a long field
 * and outputs a page at the end that contains that max value.
 * Only outputs page once all input pages are consumed.
 */
@Experimental
public class LongMaxOperator implements Operator {
    boolean finished;
    boolean returnedResult;
    long max = Long.MIN_VALUE;
    private final int channel;

    public LongMaxOperator(int channel) {
        this.channel = channel;
    }

    @Override
    public Page getOutput() {
        if (finished && returnedResult == false) {
            returnedResult = true;
            return new Page(new LongArrayBlock(new long[] { max }, 1));
        }
        return null;
    }

    @Override
    public boolean isFinished() {
        return finished && returnedResult;
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
        Block block = page.getBlock(channel);
        for (int i = 0; i < block.getPositionCount(); i++) {
            max = Math.max(block.getLong(i), max);
        }
    }

    @Override
    public void close() {

    }
}
