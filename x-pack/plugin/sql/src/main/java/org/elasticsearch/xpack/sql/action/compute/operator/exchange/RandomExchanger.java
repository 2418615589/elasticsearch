/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.sql.action.compute.operator.exchange;

import org.elasticsearch.action.support.ListenableActionFuture;
import org.elasticsearch.common.Randomness;
import org.elasticsearch.xpack.sql.action.compute.data.Page;
import org.elasticsearch.xpack.sql.action.compute.operator.Operator;

import java.util.List;
import java.util.function.Consumer;

/**
 * Exchanger implementation that randomly hands off the data to various exchange sources.
 */
public class RandomExchanger implements Exchanger {

    private final List<Consumer<Page>> buffers;

    public RandomExchanger(List<Consumer<Page>> buffers) {
        this.buffers = buffers;
    }

    @Override
    public void accept(Page page) {
        int randomIndex = Randomness.get().nextInt(buffers.size());
        buffers.get(randomIndex).accept(page);
    }

    @Override
    public ListenableActionFuture<Void> waitForWriting() {
        // TODO: implement
        return Operator.NOT_BLOCKED;
    }
}
