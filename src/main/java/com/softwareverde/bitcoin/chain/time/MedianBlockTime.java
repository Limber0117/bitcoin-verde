package com.softwareverde.bitcoin.chain.time;

import com.softwareverde.constable.Constable;
import com.softwareverde.util.type.time.Time;

public interface MedianBlockTime extends Time, Constable<ImmutableMedianBlockTime> {
    Integer BLOCK_COUNT = 11;
    Long GENESIS_BLOCK_TIMESTAMP = 1231006505L;
}
