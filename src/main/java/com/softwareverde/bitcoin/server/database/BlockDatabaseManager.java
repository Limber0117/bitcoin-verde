package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

import java.util.List;

public class BlockDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;

    public BlockDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    protected Long _getBlockIdFromHash(final Hash blockHash) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE hash = ?")
                .setParameter(BitcoinUtil.toHexString(blockHash))
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getLong("id");
    }

    protected void _updateBlockHeader(final Long blockId, final BlockHeader blockHeader) throws DatabaseException {
        final Long previousBlockId = _getBlockIdFromHash(blockHeader.getPreviousBlockHash());

        _databaseConnection.executeSql(
            new Query("UPDATE blocks SET hash = ?, previous_block_id = ?, merkle_root = ?, version = ?, timestamp = ?, difficulty = ?, nonce = ? WHERE id = ?")
                .setParameter(BitcoinUtil.toHexString(blockHeader.calculateSha256Hash()))
                .setParameter(previousBlockId)
                .setParameter(BitcoinUtil.toHexString(blockHeader.getMerkleRoot()))
                .setParameter(blockHeader.getVersion())
                .setParameter(blockHeader.getTimestamp())
                .setParameter(BitcoinUtil.toHexString(blockHeader.getDifficulty().encode()))
                .setParameter(blockHeader.getNonce())
                .setParameter(blockId)
        );
    }

    protected Long _insertBlockHeader(final BlockHeader blockHeader) throws DatabaseException {
        final Long previousBlockId = _getBlockIdFromHash(blockHeader.getPreviousBlockHash());

        return _databaseConnection.executeSql(
            new Query("INSERT INTO blocks (hash, previous_block_id, merkle_root, version, timestamp, difficulty, nonce) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .setParameter(BitcoinUtil.toHexString(blockHeader.calculateSha256Hash()))
                .setParameter(previousBlockId)
                .setParameter(BitcoinUtil.toHexString(blockHeader.getMerkleRoot()))
                .setParameter(blockHeader.getVersion())
                .setParameter(blockHeader.getTimestamp())
                .setParameter(BitcoinUtil.toHexString(blockHeader.getDifficulty().encode()))
                .setParameter(blockHeader.getNonce())
        );
    }

    public Long storeBlockHeader(final BlockHeader blockHeader) throws DatabaseException {
        final Long existingBlockId = _getBlockIdFromHash(blockHeader.calculateSha256Hash());

        if (existingBlockId != null) {
            _updateBlockHeader(existingBlockId, blockHeader);
            return existingBlockId;
        }

        final Long newBlockId = _insertBlockHeader(blockHeader);
        return newBlockId;
    }
}
