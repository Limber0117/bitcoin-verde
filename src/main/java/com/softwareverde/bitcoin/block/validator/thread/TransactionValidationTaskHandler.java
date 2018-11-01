package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;

public class TransactionValidationTaskHandler implements TaskHandler<Transaction, Boolean> {
    private final BlockchainSegmentId _blockchainSegmentId;
    private final Long _blockHeight;
    private final NetworkTime _networkTime;
    private final MedianBlockTime _medianBlockTime;
    private TransactionValidator _transactionValidator;
    private boolean _allInputsAreUnlocked = true;

    public TransactionValidationTaskHandler(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _blockchainSegmentId = blockchainSegmentId;
        _blockHeight = blockHeight;
        _networkTime = networkTime.asConst(); // NOTE: This freezes the networkTime...
        _medianBlockTime = medianBlockTime.asConst(); // NOTE: This freezes the medianBlockTime... (but shouldn't matter)
    }

    @Override
    public void init(final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
        _transactionValidator = new TransactionValidator(databaseConnection, databaseManagerCache, _networkTime, _medianBlockTime);
    }

    @Override
    public void executeTask(final Transaction transaction) {
        if (! _allInputsAreUnlocked) { return; }

        final boolean transactionInputsAreUnlocked;
        {
            boolean inputsAreUnlocked = false;
            try {
                inputsAreUnlocked = _transactionValidator.validateTransaction(_blockchainSegmentId, _blockHeight, transaction, false);
            }
            catch (final Exception exception) { Logger.log(exception); }
            transactionInputsAreUnlocked = inputsAreUnlocked;
        }

        if (! transactionInputsAreUnlocked) {
            _allInputsAreUnlocked = false;
        }
    }

    @Override
    public Boolean getResult() {
        return _allInputsAreUnlocked;
    }
}
