package com.softwareverde.bitcoin.block.header;

import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

public class ImmutableBlockHeaderWithTransactionCount extends ImmutableBlockHeader implements BlockHeaderWithTransactionCount {
    protected final Integer _transactionCount;

    public ImmutableBlockHeaderWithTransactionCount(final BlockHeader blockHeader, final Integer transactionCount) {
        super(blockHeader);
        _transactionCount = transactionCount;
    }

    @Override
    public Integer getTransactionCount() {
        return _transactionCount;
    }

    @Override
    public Json toJson() {
        final Json json = super.toJson();
        json.put("transactionCount", _transactionCount);
        return json;
    }

    @Override
    public boolean equals(final Object object) {
        if (object instanceof BlockHeaderWithTransactionCount) {
            if (! Util.areEqual(_transactionCount, ((BlockHeaderWithTransactionCount) object).getTransactionCount())) {
                return false;
            }
        }

        return super.equals(object);
    }
}
