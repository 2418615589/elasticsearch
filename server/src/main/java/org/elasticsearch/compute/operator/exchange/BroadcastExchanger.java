/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.compute.operator.exchange;

import org.elasticsearch.action.support.ListenableActionFuture;
import org.elasticsearch.common.util.concurrent.RunOnce;
import org.elasticsearch.compute.data.Page;

import java.util.List;
import java.util.function.Consumer;

/**
 * Broadcasts pages to multiple exchange sources
 */
public class BroadcastExchanger implements Exchanger {
    private final List<Consumer<ExchangeSource.PageReference>> buffers;
    private final ExchangeMemoryManager memoryManager;

    public BroadcastExchanger(List<Consumer<ExchangeSource.PageReference>> buffers, ExchangeMemoryManager memoryManager) {
        this.buffers = buffers;
        this.memoryManager = memoryManager;
    }

    @Override
    public void accept(Page page) {
        memoryManager.addPage();

        ExchangeSource.PageReference pageReference = new ExchangeSource.PageReference(page, new RunOnce(memoryManager::releasePage));

        for (Consumer<ExchangeSource.PageReference> buffer : buffers) {
            buffer.accept(pageReference);
        }
    }

    @Override
    public ListenableActionFuture<Void> waitForWriting() {
        return memoryManager.getNotFullFuture();
    }
}
