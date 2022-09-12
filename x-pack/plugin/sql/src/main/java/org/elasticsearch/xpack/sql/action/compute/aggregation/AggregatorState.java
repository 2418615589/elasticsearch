/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.sql.action.compute.aggregation;

import org.elasticsearch.core.Releasable;

public interface AggregatorState<T extends AggregatorState<T>> extends Releasable {

    AggregatorStateSerializer<T> serializer();

    @Override
    default void close() {
        // do nothing
    }
}
