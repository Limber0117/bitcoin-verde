package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.validator.*;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.ReadUncommittedDatabaseConnectionFactoryWrapper;
import com.softwareverde.bitcoin.server.database.cache.LocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.OrphanedTransactionsCache;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorFactory;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Container;
import com.softwareverde.util.RotatingQueue;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.timer.NanoTimer;

public class BlockProcessor {
    protected final Object _statisticsMutex = new Object();
    protected final RotatingQueue<Long> _blocksPerSecond = new RotatingQueue<Long>(100);
    protected final RotatingQueue<Integer> _transactionsPerBlock = new RotatingQueue<Integer>(100);
    protected final Container<Float> _averageBlocksPerSecond = new Container<Float>(0F);
    protected final Container<Float> _averageTransactionsPerSecond = new Container<Float>(0F);
    protected final NetworkTime _networkTime;

    protected final BlockInflaters _blockInflaters;
    protected final BlockValidatorFactory _blockValidatorFactory;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final TransactionValidatorFactory _transactionValidatorFactory;
    protected final MutableMedianBlockTime _medianBlockTime;
    protected final MasterDatabaseManagerCache _masterDatabaseManagerCache;
    protected final OrphanedTransactionsCache _orphanedTransactionsCache;

    protected Integer _maxThreadCount = 4;
    protected Long _trustedBlockHeight = 0L;

    protected Integer _processedBlockCount = 0;
    protected final Long _startTime;

    public BlockProcessor(final FullNodeDatabaseManagerFactory databaseManagerFactory, final MasterDatabaseManagerCache masterDatabaseManagerCache, final TransactionValidatorFactory transactionValidatorFactory, final NetworkTime networkTime, final MutableMedianBlockTime medianBlockTime, final OrphanedTransactionsCache orphanedTransactionsCache) {
        this(
            databaseManagerFactory,
            masterDatabaseManagerCache,
            new CoreInflater(),
            transactionValidatorFactory,
            networkTime,
            medianBlockTime,
            orphanedTransactionsCache
        );
    }

    public BlockProcessor(final FullNodeDatabaseManagerFactory databaseManagerFactory, final MasterDatabaseManagerCache masterDatabaseManagerCache, final BlockInflaters blockInflaters, final TransactionValidatorFactory transactionValidatorFactory, final NetworkTime networkTime, final MutableMedianBlockTime medianBlockTime, final OrphanedTransactionsCache orphanedTransactionsCache) {
        this(
            databaseManagerFactory,
            masterDatabaseManagerCache,
            blockInflaters,
            new BlockValidatorFactoryCore(),
            transactionValidatorFactory,
            networkTime,
            medianBlockTime,
            orphanedTransactionsCache
        );
    }

    public BlockProcessor(final FullNodeDatabaseManagerFactory databaseManagerFactory, final MasterDatabaseManagerCache masterDatabaseManagerCache, final BlockInflaters blockInflaters, final BlockValidatorFactory blockValidatorFactory, final TransactionValidatorFactory transactionValidatorFactory, final NetworkTime networkTime, final MutableMedianBlockTime medianBlockTime, final OrphanedTransactionsCache orphanedTransactionsCache) {
        _databaseManagerFactory = databaseManagerFactory;
        _masterDatabaseManagerCache = masterDatabaseManagerCache;
        _blockInflaters = blockInflaters;
        _transactionValidatorFactory = transactionValidatorFactory;
        _blockValidatorFactory = blockValidatorFactory;

        _medianBlockTime = medianBlockTime;
        _networkTime = networkTime;

        _startTime = System.currentTimeMillis();

        _orphanedTransactionsCache = orphanedTransactionsCache;
    }

    public void setMaxThreadCount(final Integer maxThreadCount) {
        _maxThreadCount = maxThreadCount;
    }

    public void setTrustedBlockHeight(final Long trustedBlockHeight) {
        _trustedBlockHeight = trustedBlockHeight;
    }

    protected Long _processBlock(final Block block) throws DatabaseException {
        try (
            final LocalDatabaseManagerCache localDatabaseManagerCache = new LocalDatabaseManagerCache(_masterDatabaseManagerCache);
            final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager(localDatabaseManagerCache)
        ) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final Sha256Hash blockHash = block.getHash();
            _processedBlockCount += 1;

            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final BlockchainSegmentId originalHeadBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final BlockId blockId;
            final Boolean blockHeaderExists = blockHeaderDatabaseManager.blockHeaderExists(blockHash);
            if (blockHeaderExists) {
                final Boolean blockHasTransactions = blockDatabaseManager.hasTransactions(blockHash);
                if (blockHasTransactions) {
                    Logger.debug("Skipping known block: " + blockHash);
                    final BlockId existingBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                    return blockHeaderDatabaseManager.getBlockHeight(existingBlockId);
                }

                blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
            }
            else {
                // Store the BlockHeader...
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    final NanoTimer storeBlockHeaderTimer = new NanoTimer();

                    TransactionUtil.startTransaction(databaseConnection);
                    {
                        Logger.debug("Processing Block: " + blockHash);
                        final Boolean blockHasTransactions = blockDatabaseManager.hasTransactions(blockHash);
                        if (blockHasTransactions) {
                            Logger.debug("Skipping known block: " + blockHash);
                            final BlockId existingBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                            return blockHeaderDatabaseManager.getBlockHeight(existingBlockId);
                        }

                        storeBlockHeaderTimer.start();
                        blockId = blockHeaderDatabaseManager.storeBlockHeader(block);

                        if (blockId == null) {
                            Logger.debug("Error storing BlockHeader: " + blockHash);
                            TransactionUtil.rollbackTransaction(databaseConnection);
                            return null;
                        }

                        final BlockHeaderValidator blockHeaderValidator = _blockValidatorFactory.newBlockHeaderValidator(databaseManager, _networkTime, _medianBlockTime);
                        final BlockHeaderValidator.BlockHeaderValidationResponse blockHeaderValidationResponse = blockHeaderValidator.validateBlockHeader(block);
                        if (! blockHeaderValidationResponse.isValid) {
                            Logger.debug("Invalid BlockHeader: " + blockHeaderValidationResponse.errorMessage + " (" + blockHash + ")");
                            TransactionUtil.rollbackTransaction(databaseConnection);
                            return null;
                        }

                        storeBlockHeaderTimer.stop();
                    }
                    TransactionUtil.commitTransaction(databaseConnection);
                }
            }

            final NanoTimer storeBlockTimer = new NanoTimer();
            final NanoTimer blockValidationTimer = new NanoTimer();
            TransactionUtil.startTransaction(databaseConnection);
            {
                storeBlockTimer.start();
                final Boolean transactionsStoredSuccessfully = blockDatabaseManager.storeBlockTransactions(block); // Store the Block's transactions (the BlockHeader should have already been stored above)...
                storeBlockTimer.stop();

                if (! transactionsStoredSuccessfully) {
                    TransactionUtil.rollbackTransaction(databaseConnection);
                    Logger.debug("Invalid block. Unable to store transactions for block: " + blockHash);
                    return null;
                }

                final int transactionCount = block.getTransactions().getSize();
                Logger.info("Stored " + transactionCount + " transactions in " + (String.format("%.2f", storeBlockTimer.getMillisecondsElapsed())) + "ms (" + String.format("%.2f", ((((double) transactionCount) / storeBlockTimer.getMillisecondsElapsed()) * 1000)) + " tps). " + block.getHash());

                final Boolean blockIsValid;
                { // NOTE: The DatabaseConnectionFactoryWrapper should not be closed.
                    final DatabaseConnectionFactory databaseConnectionFactory = _databaseManagerFactory.getDatabaseConnectionFactory();
                    final ReadUncommittedDatabaseConnectionFactoryWrapper readUncommittedDatabaseConnectionFactoryWrapper = new ReadUncommittedDatabaseConnectionFactoryWrapper(databaseConnectionFactory);
                    final FullNodeDatabaseManagerFactory databaseManagerFactory = _databaseManagerFactory.newDatabaseManagerFactory(readUncommittedDatabaseConnectionFactoryWrapper, localDatabaseManagerCache);

                    final BlockValidator blockValidator = _blockValidatorFactory.newBlockValidator(databaseManagerFactory, _transactionValidatorFactory, _networkTime, _medianBlockTime);
                    blockValidator.setMaxThreadCount(_maxThreadCount);
                    blockValidator.setTrustedBlockHeight(_trustedBlockHeight);
                    blockValidator.setShouldLogValidBlocks(true);

                    blockValidationTimer.start();
                    final BlockValidationResult blockValidationResult = blockValidator.validateBlockTransactions(blockId, block); // NOTE: Only validates the transactions since the blockHeader is validated separately above...
                    if (! blockValidationResult.isValid) {
                        Logger.info(blockValidationResult.errorMessage);
                    }
                    blockIsValid = blockValidationResult.isValid;
                    blockValidationTimer.stop();

                    // localDatabaseManagerCache.log();
                    localDatabaseManagerCache.resetLog();
                }

                if (! blockIsValid) {
                    TransactionUtil.rollbackTransaction(databaseConnection);
                    Logger.debug("Invalid block. Transactions did not validate for block: " + blockHash);
                    return null;
                }
            }
            TransactionUtil.commitTransaction(databaseConnection);

            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);

            final BlockDeflater blockDeflater = _blockInflaters.getBlockDeflater();
            final Integer byteCount = blockDeflater.getByteCount(block);
            blockHeaderDatabaseManager.setBlockByteCount(blockId, byteCount);

            _medianBlockTime.addBlock(block);

            final BlockchainSegmentId newHeadBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final Boolean bestBlockchainHasChanged = (! Util.areEqual(newHeadBlockchainSegmentId, originalHeadBlockchainSegmentId));

            { // Maintain memory-pool correctness...
                if (bestBlockchainHasChanged) {
                    // TODO: Mempool Reorgs should write/read-lock the mempool until complete...

                    final MilliTimer timer = new MilliTimer();
                    Logger.trace("Starting Unspent Transactions Reorganization: " + originalHeadBlockchainSegmentId + " -> " + newHeadBlockchainSegmentId);
                    timer.start();
                    // Rebuild the memory pool to include (valid) transactions that were broadcast/mined on the old chain but were excluded from the new chain...
                    // 1. Take the block at the head of the old chain and add its transactions back into the pool... (Ignoring the coinbases...)
                    BlockId nextBlockId = blockchainDatabaseManager.getHeadBlockIdOfBlockchainSegment(originalHeadBlockchainSegmentId);

                    while (nextBlockId != null) {
                        final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>(blockDatabaseManager.getTransactionIds(nextBlockId));
                        transactionIds.remove(0); // Exclude the coinbase...
                        transactionDatabaseManager.addToUnconfirmedTransactions(transactionIds);

                        // 2. Continue to traverse up the chain until the block is connected to the new headBlockchain...
                        nextBlockId = blockHeaderDatabaseManager.getAncestorBlockId(nextBlockId, 1);
                        final Boolean nextBlockIsConnectedToNewHeadBlockchain = blockHeaderDatabaseManager.isBlockConnectedToChain(nextBlockId, newHeadBlockchainSegmentId, BlockRelationship.ANCESTOR);
                        if (nextBlockIsConnectedToNewHeadBlockchain) { break; }
                    }
                    Logger.trace("Utxo Reorg - 2/5 complete.");

                    // 2.5 Skip the shared block between the two segments (not strictly necessary, but more performant)...
                    nextBlockId = blockHeaderDatabaseManager.getChildBlockId(newHeadBlockchainSegmentId, nextBlockId);

                    // 3. Traverse down the chain to the new head of the chain and remove the transactions from those blocks from the memory pool...
                    while (nextBlockId != null) {
                        final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>(blockDatabaseManager.getTransactionIds(nextBlockId));
                        transactionIds.remove(0); // Exclude the coinbase (not strictly necessary, but performs slightly better)...
                        transactionDatabaseManager.removeFromUnconfirmedTransactions(transactionIds);

                        nextBlockId = blockHeaderDatabaseManager.getChildBlockId(newHeadBlockchainSegmentId, nextBlockId);
                    }
                    Logger.trace("Utxo Reorg - 3/5 complete.");

                    // 4. Validate that the transactions are still valid on the new chain...
                    final TransactionValidator transactionValidator = _transactionValidatorFactory.newTransactionValidator(databaseManager, _networkTime, _medianBlockTime);
                    transactionValidator.setLoggingEnabled(false);

                    final List<TransactionId> transactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();
                    final MutableList<TransactionId> transactionsToRemove = new MutableList<TransactionId>();
                    for (final TransactionId transactionId : transactionIds) {
                        final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                        final Boolean transactionIsValid = transactionValidator.validateTransaction(newHeadBlockchainSegmentId, blockHeight, transaction, true);
                        if (! transactionIsValid) {
                            transactionsToRemove.add(transactionId);
                        }
                    }

                    Logger.trace("Utxo Reorg - 4/5 complete.");

                    // 5. Remove transactions in UnconfirmedTransactions that depend on the removed transactions...
                    while (! transactionsToRemove.isEmpty()) {
                        transactionDatabaseManager.removeFromUnconfirmedTransactions(transactionsToRemove);
                        final List<TransactionId> chainedInvalidTransactions = transactionDatabaseManager.getUnconfirmedTransactionsDependingOn(transactionsToRemove);
                        transactionsToRemove.clear();
                        transactionsToRemove.addAll(chainedInvalidTransactions);
                    }
                    timer.stop();
                    Logger.info("Unspent Transactions Reorganization: " + originalHeadBlockchainSegmentId + " -> " + newHeadBlockchainSegmentId + " (" + timer.getMillisecondsElapsed() + "ms)");
                }
                else {
                    // Remove any transactions in the memory pool that were included in this block...
                    final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>(blockDatabaseManager.getTransactionIds(blockId));
                    transactionIds.remove(0); // Exclude the coinbase (not strictly necessary, but performs slightly better)...
                    transactionDatabaseManager.removeFromUnconfirmedTransactions(transactionIds);

                    // Remove any transactions in the memory pool that are now considered double-spends...
                    final MutableList<TransactionId> transactionsToRemove = new MutableList<TransactionId>(transactionDatabaseManager.getUnconfirmedTransactionsDependingOnSpentInputsOf(transactionIds));
                    while (! transactionsToRemove.isEmpty()) {
                        transactionDatabaseManager.removeFromUnconfirmedTransactions(transactionsToRemove);
                        final List<TransactionId> chainedInvalidTransactions = transactionDatabaseManager.getUnconfirmedTransactionsDependingOn(transactionsToRemove);
                        transactionsToRemove.clear();
                        transactionsToRemove.addAll(chainedInvalidTransactions);
                    }
                }
            }

            final Integer blockTransactionCount = block.getTransactions().getSize();

            final Float averageBlocksPerSecond;
            final Float averageTransactionsPerSecond;
            synchronized (_statisticsMutex) {
                _blocksPerSecond.add(Math.round(blockValidationTimer.getMillisecondsElapsed() + storeBlockTimer.getMillisecondsElapsed()));
                _transactionsPerBlock.add(blockTransactionCount);

                final Integer blockCount = _blocksPerSecond.size();
                final Long validationTimeElapsed;
                {
                    long value = 0L;
                    for (final Long elapsed : _blocksPerSecond) {
                        value += elapsed;
                    }
                    validationTimeElapsed = value;
                }

                final Integer totalTransactionCount;
                {
                    int value = 0;
                    for (final Integer transactionCount : _transactionsPerBlock) {
                        value += transactionCount;
                    }
                    totalTransactionCount = value;
                }

                averageBlocksPerSecond = ( (blockCount.floatValue() / validationTimeElapsed.floatValue()) * 1000F );
                averageTransactionsPerSecond = ( (totalTransactionCount.floatValue() / validationTimeElapsed.floatValue()) * 1000F );
            }

            // _averageBlocksPerSecond.value = averageBlocksPerSecond;
            // final Long now = System.currentTimeMillis();
            _averageBlocksPerSecond.value = averageBlocksPerSecond; // ((_processedBlockCount.floatValue() / (now - _startTime)) * 1000.0F);
            _averageTransactionsPerSecond.value = averageTransactionsPerSecond;

            _masterDatabaseManagerCache.commitLocalDatabaseManagerCache(localDatabaseManagerCache);
            _masterDatabaseManagerCache.commit();

            return blockHeight;
        }
    }

    public Long processBlock(final Block block) {
        try {
            final Long newBlockHeight = _processBlock(block);
            final Boolean blockWasValid = (newBlockHeight != null);
            if ((blockWasValid) && (_orphanedTransactionsCache != null)) {
                for (final Transaction transaction : block.getTransactions()) {
                    _orphanedTransactionsCache.onTransactionAdded(transaction);
                }
            }

            return newBlockHeight;
        }
        catch (final Exception exception) {
            Logger.info("ERROR VALIDATING BLOCK: " + block.getHash(), exception);
        }

        return null;
    }

    public Container<Float> getAverageBlocksPerSecondContainer() {
        return _averageBlocksPerSecond;
    }

    public Container<Float> getAverageTransactionsPerSecondContainer() {
        return _averageTransactionsPerSecond;
    }
}
