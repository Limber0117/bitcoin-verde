package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.ImmutableDifficulty;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.type.merkleroot.ImmutableMerkleRoot;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.constable.Const;

public class ImmutableBlockHeader implements BlockHeader, Const {
    protected final BlockHeader _blockHeader;
    protected Hash _cachedBlockHeader = null;

    public ImmutableBlockHeader() {
        _blockHeader = new MutableBlockHeader();
        _cachedBlockHeader = _blockHeader.getHash();
    }

    public ImmutableBlockHeader(final BlockHeader blockHeader) {
        if (blockHeader instanceof ImmutableBlockHeader) {
            _blockHeader = blockHeader;
            return;
        }

        final MutableBlockHeader mutableBlockHeader = new MutableBlockHeader();
        mutableBlockHeader.setVersion(blockHeader.getVersion());
        mutableBlockHeader.setPreviousBlockHash(new ImmutableHash(blockHeader.getPreviousBlockHash()));
        mutableBlockHeader.setMerkleRoot(new ImmutableMerkleRoot(blockHeader.getMerkleRoot()));
        mutableBlockHeader.setTimestamp(blockHeader.getTimestamp());
        mutableBlockHeader.setDifficulty(new ImmutableDifficulty(blockHeader.getDifficulty()));
        mutableBlockHeader.setNonce(blockHeader.getNonce());
        _blockHeader = mutableBlockHeader;
    }

    @Override
    public Integer getVersion() {
        return _blockHeader.getVersion();
    }

    @Override
    public Hash getPreviousBlockHash() {
        return _blockHeader.getPreviousBlockHash();
    }

    @Override
    public MerkleRoot getMerkleRoot() {
        return _blockHeader.getMerkleRoot();
    }

    @Override
    public Long getTimestamp() {
        return _blockHeader.getTimestamp();
    }

    @Override
    public Difficulty getDifficulty() {
        return _blockHeader.getDifficulty();
    }

    @Override
    public Long getNonce() {
        return _blockHeader.getNonce();
    }

    @Override
    public Hash getHash() {
        if (_cachedBlockHeader == null) {
            _cachedBlockHeader = _blockHeader.getHash();
        }

        return _cachedBlockHeader;
    }

    @Override
    public Boolean isValid() {
        return _blockHeader.isValid();
    }

    @Override
    public ImmutableBlockHeader asConst() {
        return this;
    }
}
