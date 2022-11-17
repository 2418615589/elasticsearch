/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.compute.aggregation;

import org.elasticsearch.compute.Experimental;
import org.elasticsearch.compute.data.AggregatorStateBlock;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.LongArrayBlock;
import org.elasticsearch.compute.data.Page;

@Experimental
public class CountRowsAggregator implements AggregatorFunction {

    private final LongState state;
    private final int channel;

    static CountRowsAggregator create(int inputChannel) {
        if (inputChannel < 0) {
            throw new IllegalArgumentException();
        }
        return new CountRowsAggregator(inputChannel, new LongState());
    }

    static CountRowsAggregator createIntermediate() {
        return new CountRowsAggregator(-1, new LongState());
    }

    private CountRowsAggregator(int channel, LongState state) {
        this.channel = channel;
        this.state = state;
    }

    @Override
    public void addRawInput(Page page) {
        assert channel >= 0;
        Block block = page.getBlock(channel);
        LongState state = this.state;
        state.longValue(state.longValue() + block.getPositionCount() - block.nullValuesCount()); // ignore null values
    }

    @Override
    public void addIntermediateInput(Block block) {
        assert channel == -1;
        if (block instanceof AggregatorStateBlock) {
            @SuppressWarnings("unchecked")
            AggregatorStateBlock<LongState> blobBlock = (AggregatorStateBlock<LongState>) block;
            LongState state = this.state;
            LongState tmpState = new LongState();
            for (int i = 0; i < block.getPositionCount(); i++) {
                blobBlock.get(i, tmpState);
                state.longValue(state.longValue() + tmpState.longValue());
            }
        } else {
            throw new RuntimeException("expected AggregatorStateBlock, got:" + block);
        }
    }

    @Override
    public Block evaluateIntermediate() {
        AggregatorStateBlock.Builder<AggregatorStateBlock<LongState>, LongState> builder = AggregatorStateBlock.builderOfAggregatorState(
            LongState.class,
            state.getEstimatedSize()
        );
        builder.add(state);
        return builder.build();
    }

    @Override
    public Block evaluateFinal() {
        return new LongArrayBlock(new long[] { state.longValue() }, 1);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("[");
        sb.append("channel=").append(channel);
        sb.append("]");
        return sb.toString();
    }
}
