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

@Experimental
public class LongAvgOperator implements Operator {
    boolean finished;
    boolean returnedResult;
    long count;
    long sum;
    private final int rawChannel;
    private final int sumChannel;
    private final int countChannel;

    // PARTIAL
    public LongAvgOperator(int rawChannel) {
        this.rawChannel = rawChannel;
        this.sumChannel = -1;
        this.countChannel = -1;
    }

    // FINAL
    public LongAvgOperator(int sumChannel, int countChannel) {
        this.rawChannel = -1;
        this.sumChannel = sumChannel;
        this.countChannel = countChannel;
    }

    @Override
    public void close() { /* no-op */ }

    @Override
    public Page getOutput() {
        if (finished && returnedResult == false) {
            returnedResult = true;
            if (rawChannel != -1) {
                return new Page(new LongArrayBlock(new long[] { sum }, 1), new LongArrayBlock(new long[] { count }, 1));
            } else {
                return new Page(new LongArrayBlock(new long[] { sum / count }, 1));
            }
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
        if (rawChannel != -1) {
            Block block = page.getBlock(rawChannel);
            for (int i = 0; i < block.getPositionCount(); i++) {
                sum += block.getLong(i);
            }
            count += block.getPositionCount();
        } else {
            Block sumBlock = page.getBlock(sumChannel);
            Block countBlock = page.getBlock(countChannel);
            for (int i = 0; i < page.getPositionCount(); i++) {
                sum += sumBlock.getLong(i);
                count += countBlock.getLong(i);
            }
        }
    }
}
