package com.softwareverde.bitcoin.chain.segment;

import com.softwareverde.bitcoin.block.BlockId;

public class BlockchainSegment {
    protected BlockchainSegmentId _id;
    protected BlockId _headBlockId;
    protected BlockId _tailBlockId;
    protected Long _blockHeight;
    protected Long _blockCount;

    protected BlockchainSegment() { }

    public BlockchainSegmentId getId() {
        return _id;
    }

    public BlockId getHeadBlockId() {
        return _headBlockId;
    }

    public BlockId getTailBlockId() {
        return _tailBlockId;
    }

    public Long getBlockHeight() {
        return _blockHeight;
    }

    public Long getBlockCount() {
        return _blockCount;
    }
}
