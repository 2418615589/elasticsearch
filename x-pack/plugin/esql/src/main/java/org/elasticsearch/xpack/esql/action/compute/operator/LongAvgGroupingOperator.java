/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.action.compute.operator;

import org.elasticsearch.xpack.esql.action.compute.data.Block;
import org.elasticsearch.xpack.esql.action.compute.data.LongBlock;
import org.elasticsearch.xpack.esql.action.compute.data.Page;

import java.util.HashMap;
import java.util.Map;

public class LongAvgGroupingOperator implements Operator {
    boolean finished;
    boolean returnedResult;
    Page lastPage;

    private final int groupChannel;
    private final int valueChannel;

    // trivial implementation based on Java's HashMap
    private Map<Long, GroupSum> sums;

    public LongAvgGroupingOperator(int valueChannel, int groupChannel) {
        this.valueChannel = valueChannel;
        this.groupChannel = groupChannel;
        sums = new HashMap<>();
    }

    @Override
    public Page getOutput() {
        Page l = lastPage;
        if (l == null) {
            return null; // not ready
        }
        lastPage = null;
        if (finished) {
            sums = null;
        }
        return l;
    }

    @Override
    public void close() { /* no-op */ }

    @Override
    public boolean isFinished() {
        return finished && lastPage == null;
    }

    @Override
    public void finish() {
        if (finished) {
            return;
        }
        finished = true;

        int len = sums.size();
        long[] groups = new long[len];
        long[] averages = new long[len];
        int i = 0;
        for (var e : sums.entrySet()) {
            groups[i] = e.getKey();
            var groupSum = e.getValue();
            averages[i] = groupSum.sum / groupSum.count;
            i++;
        }
        Block groupBlock = new LongBlock(groups, len);
        Block averagesBlock = new LongBlock(averages, len);
        lastPage = new Page(groupBlock, averagesBlock);
    }

    @Override
    public boolean needsInput() {
        return finished == false && lastPage == null;
    }

    static class GroupSum {
        long count;
        long sum;
    }

    @Override
    public void addInput(Page page) {
        Block groupBlock = page.getBlock(groupChannel);
        Block valuesBlock = page.getBlock(valueChannel);
        assert groupBlock.getPositionCount() == valuesBlock.getPositionCount();
        int len = groupBlock.getPositionCount();
        for (int i = 0; i < len; i++) {
            long group = groupBlock.getLong(i);
            long value = valuesBlock.getLong(i);
            var groupSum = sums.computeIfAbsent(group, k -> new GroupSum());
            groupSum.sum += value;
            groupSum.count++;
        }
    }
}
