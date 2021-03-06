package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
import com.softwareverde.bitcoin.slp.validator.SlpTransactionValidationCache;
import com.softwareverde.bitcoin.slp.validator.SlpTransactionValidator;
import com.softwareverde.bitcoin.slp.validator.TransactionAccumulator;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.timer.MilliTimer;

import java.util.HashMap;
import java.util.Map;

public class SlpTransactionProcessor extends SleepyService {
    public static final Integer BATCH_SIZE = 4096;

    protected static Boolean _isConnected(final BlockchainSegmentId blockchainSegmentId, final TransactionId transactionId, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

        final List<BlockId> blockIds = transactionDatabaseManager.getBlockIds(transactionId);
        for (final BlockId blockId : blockIds) {
            final Boolean isConnected = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, blockchainSegmentId, BlockRelationship.ANCESTOR);
            if (isConnected) {
                return true;
            }
        }

        return false;
    }

    public static TransactionAccumulator createTransactionAccumulator(final Container<BlockchainSegmentId> blockchainSegmentId, final FullNodeDatabaseManager databaseManager, final Container<Integer> nullableTransactionLookupCount) {
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

        return new TransactionAccumulator() {
                @Override
                public Map<Sha256Hash, Transaction> getTransactions(final List<Sha256Hash> transactionHashes, final Boolean allowUnconfirmedTransactions) {
                    try {
                        final HashMap<Sha256Hash, Transaction> transactions = new HashMap<Sha256Hash, Transaction>(transactionHashes.getSize());
                        final MilliTimer milliTimer = new MilliTimer();
                        milliTimer.start();
                        for (final Sha256Hash transactionHash : transactionHashes) {
                            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                            if (transactionId == null) { continue; }

                            final Boolean transactionIsConnectedToBlockchainSegment = _isConnected(blockchainSegmentId.value, transactionId, databaseManager);
                            if (! transactionIsConnectedToBlockchainSegment) {
                                if (! (allowUnconfirmedTransactions && transactionDatabaseManager.isUnconfirmedTransaction(transactionId))) {
                                    continue;
                                }
                            }

                            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                            transactions.put(transactionHash, transaction);
                        }
                        milliTimer.stop();
                        if (nullableTransactionLookupCount != null) {
                            nullableTransactionLookupCount.value += transactionHashes.getSize();
                        }
                        Logger.trace("Loaded " + transactionHashes.getSize() + " in " + milliTimer.getMillisecondsElapsed() + "ms.");
                        return transactions;
                    }
                    catch (final DatabaseException exception) {
                        Logger.warn(exception);
                        return null;
                    }
                }
            };
    }

    public static SlpTransactionValidationCache createSlpTransactionValidationCache(final Container<BlockchainSegmentId> blockchainSegmentId, final FullNodeDatabaseManager databaseManager) {
        final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
        final SlpTransactionDatabaseManager slpTransactionDatabaseManager = databaseManager.getSlpTransactionDatabaseManager();

        return new SlpTransactionValidationCache() {
            @Override
            public Boolean isValid(final Sha256Hash transactionHash) {
                try {
                    final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                    if (transactionId == null) { return null; }

                    final MilliTimer milliTimer = new MilliTimer();
                    milliTimer.start();
                    final Boolean result = slpTransactionDatabaseManager.getSlpTransactionValidationResult(blockchainSegmentId.value, transactionId);
                    milliTimer.stop();
                    Logger.trace("Loaded Cached Validity: " + transactionHash + " in " + milliTimer.getMillisecondsElapsed() + "ms. (" + result + ")");
                    return result;
                }
                catch (final DatabaseException exception) {
                    Logger.warn(exception);
                    return null;
                }
            }

            @Override
            public void setIsValid(final Sha256Hash transactionHash, final Boolean isValid) {
                try {
                    final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                    if (transactionId == null) { return; }

                    slpTransactionDatabaseManager.setSlpTransactionValidationResult(blockchainSegmentId.value, transactionId, isValid);
                }
                catch (final DatabaseException exception) {
                    Logger.warn(exception);
                }
            }
        };
    }

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    @Override
    protected void _onStart() {
        Logger.trace("SlpTransactionProcessor Starting.");
    }

    @Override
    protected Boolean _run() {
        Logger.trace("SlpTransactionProcessor Running.");
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final SlpTransactionDatabaseManager slpTransactionDatabaseManager = databaseManager.getSlpTransactionDatabaseManager();

            final Container<BlockchainSegmentId> blockchainSegmentId = new Container<BlockchainSegmentId>();
            final Container<Integer> transactionLookupCount = new Container<Integer>(0);

            final TransactionAccumulator transactionAccumulator = SlpTransactionProcessor.createTransactionAccumulator(blockchainSegmentId, databaseManager, transactionLookupCount);
            final SlpTransactionValidationCache slpTransactionValidationCache = SlpTransactionProcessor.createSlpTransactionValidationCache(blockchainSegmentId, databaseManager);

            // 1. Iterate through blocks for SLP transactions.
            // 2. Validate any SLP transactions for that block's blockchain segment.
            // 3. Update those SLP transactions validation statuses via the slpTransactionDatabaseManager.
            // 4. Move on to the next block.

            final SlpTransactionValidator slpTransactionValidator = new SlpTransactionValidator(transactionAccumulator, slpTransactionValidationCache);
            final Map<BlockId, List<TransactionId>> pendingSlpTransactionIds = slpTransactionDatabaseManager.getConfirmedPendingValidationSlpTransactions(BATCH_SIZE);
            final List<TransactionId> unconfirmedPendingSlpTransactionIds;
            if (pendingSlpTransactionIds.isEmpty()) { // Only validate unconfirmed SLP Transactions if the history is up to date in order to reduce the validation depth.
                unconfirmedPendingSlpTransactionIds = slpTransactionDatabaseManager.getUnconfirmedPendingValidationSlpTransactions(BATCH_SIZE);
                if (unconfirmedPendingSlpTransactionIds.isEmpty()) { return false; }
                blockchainSegmentId.value = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            }
            else {
                unconfirmedPendingSlpTransactionIds = new MutableList<TransactionId>(0);
            }

            final MilliTimer milliTimer = new MilliTimer();

            // Validate Confirmed SLP Transactions...
            for (final BlockId blockId : pendingSlpTransactionIds.keySet()) {
                blockchainSegmentId.value = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);

                final List<TransactionId> transactionIds = pendingSlpTransactionIds.get(blockId);
                for (final TransactionId transactionId : transactionIds) {
                    transactionLookupCount.value = 0;
                    milliTimer.start();

                    final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                    final Boolean isValid = slpTransactionValidator.validateTransaction(transaction);
                    slpTransactionDatabaseManager.setSlpTransactionValidationResult(blockchainSegmentId.value, transactionId, isValid);

                    milliTimer.stop();
                    Logger.trace("Validated Slp Tx " + transaction.getHash() + " in " + milliTimer.getMillisecondsElapsed() + "ms. IsValid: " + isValid + " (lookUps=" + transactionLookupCount.value + ")");
                }
            }

            // Validate Unconfirmed SLP Transactions...
            for (final TransactionId transactionId : unconfirmedPendingSlpTransactionIds) {
                transactionLookupCount.value = 0;
                milliTimer.start();

                final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                final Boolean isValid = slpTransactionValidator.validateTransaction(transaction);
                slpTransactionDatabaseManager.setSlpTransactionValidationResult(blockchainSegmentId.value, transactionId, isValid);

                milliTimer.stop();
                Logger.trace("Validated Unconfirmed Slp Tx " + transaction.getHash() + " in " + milliTimer.getMillisecondsElapsed() + "ms. IsValid: " + isValid + " (lookUps=" + transactionLookupCount.value + ")");
            }
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return false;
        }

        Logger.trace("SlpTransactionProcessor Stopping.");
        return true;
    }

    @Override
    protected void _onSleep() {
        Logger.trace("SlpTransactionProcessor Sleeping.");
    }

    public SlpTransactionProcessor(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }
}
