/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.compute.aggregation;

import org.elasticsearch.compute.Experimental;
import org.elasticsearch.core.Releasable;

@Experimental
public interface AggregatorState<T extends AggregatorState<T>> extends Releasable {

    AggregatorStateSerializer<T> serializer();

    @Override
    default void close() {
        // do nothing
    }
}
