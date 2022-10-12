/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.compute.operator.exchange;

import org.elasticsearch.action.support.ListenableActionFuture;
import org.elasticsearch.compute.Experimental;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.operator.Operator;

/**
 * Source operator implementation that retrieves data from an {@link ExchangeSource}
 */
@Experimental
public class ExchangeSourceOperator implements Operator {

    private final ExchangeSource source;
    private ListenableActionFuture<Void> isBlocked = NOT_BLOCKED;

    public ExchangeSourceOperator(ExchangeSource source) {
        this.source = source;
    }

    @Override
    public Page getOutput() {
        return source.removePage();
    }

    @Override
    public boolean isFinished() {
        return source.isFinished();
    }

    @Override
    public void finish() {
        source.finish();
    }

    @Override
    public boolean needsInput() {
        return false;
    }

    @Override
    public void addInput(Page page) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListenableActionFuture<Void> isBlocked() {
        if (isBlocked.isDone()) {
            isBlocked = source.waitForReading();
            if (isBlocked.isDone()) {
                isBlocked = NOT_BLOCKED;
            }
        }
        return isBlocked;
    }

    @Override
    public void close() {
        source.close();
    }
}
