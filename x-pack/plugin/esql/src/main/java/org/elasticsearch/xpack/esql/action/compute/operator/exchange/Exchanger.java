/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.action.compute.operator.exchange;

import org.elasticsearch.action.support.ListenableActionFuture;
import org.elasticsearch.xpack.esql.action.compute.data.Page;
import org.elasticsearch.xpack.esql.action.compute.operator.Operator;

/**
 * Exchangers provide different means for handing off data to exchange sources, e.g. allow multiplexing.
 */
public interface Exchanger {

    void accept(Page page);

    default void finish() {

    }

    ListenableActionFuture<Void> waitForWriting();

    Exchanger FINISHED = new Exchanger() {
        @Override
        public void accept(Page page) {}

        @Override
        public ListenableActionFuture<Void> waitForWriting() {
            return Operator.NOT_BLOCKED;
        }
    };
}
