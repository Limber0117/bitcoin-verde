package com.softwareverde.bitcoin.server.module.node.sync.block;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.database.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.pending.PendingTransactionId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.timer.MilliTimer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlockDownloader extends SleepyService {
    public static final Integer MAX_DOWNLOAD_FAILURE_COUNT = 10;

    protected static final Long MAX_TIMEOUT = 90000L;

    protected final Object _downloadCallbackPin = new Object();

    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseCache;
    protected final Map<Sha256Hash, MilliTimer> _currentBlockDownloadSet = new ConcurrentHashMap<Sha256Hash, MilliTimer>();
    protected final BitcoinNodeManager.DownloadBlockCallback _blockDownloadedCallback;

    protected Runnable _newBlockAvailableCallback = null;

    protected void _onBlockDownloaded(final Block block, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);

        pendingBlockDatabaseManager.storeBlock(block);
    }

    protected void _markPendingBlockIdsAsFailed(final Set<Sha256Hash> pendingBlockHashes) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
            for (final Sha256Hash pendingBlockHash : pendingBlockHashes) {
                final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.getPendingBlockId(pendingBlockHash);
                if (pendingBlockId == null) { continue; }

                pendingBlockDatabaseManager.incrementFailedDownloadCount(pendingBlockId);
            }
            pendingBlockDatabaseManager.purgeFailedPendingBlocks(MAX_DOWNLOAD_FAILURE_COUNT);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() {
        final Integer maximumConcurrentDownloadCount = Math.max(1, _bitcoinNodeManager.getActiveNodeCount());

        { // Determine if routine should wait for a request to complete...
            Integer currentDownloadCount = _currentBlockDownloadSet.size();
            while (currentDownloadCount >= maximumConcurrentDownloadCount) {
                synchronized (_downloadCallbackPin) {
                    final MilliTimer waitTimer = new MilliTimer();
                    try {
                        waitTimer.start();
                        _downloadCallbackPin.wait(MAX_TIMEOUT);
                        waitTimer.stop();
                    }
                    catch (final InterruptedException exception) { return false; }

                    if (waitTimer.getMillisecondsElapsed() > MAX_TIMEOUT) {
                        Logger.log("NOTICE: Block download stalled.");

                        _markPendingBlockIdsAsFailed(_currentBlockDownloadSet.keySet());
                        _currentBlockDownloadSet.clear();
                        return false;
                    }
                }

                currentDownloadCount = _currentBlockDownloadSet.size();
            }
        }

        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final List<BitcoinNode> nodes = _bitcoinNodeManager.getNodes();

            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            final HashMap<NodeId, BitcoinNode> nodeMap = new HashMap<NodeId, BitcoinNode>();
            final List<NodeId> nodeIds;
            {
                final ImmutableListBuilder<NodeId> listBuilder = new ImmutableListBuilder<NodeId>(nodes.getSize());
                for (final BitcoinNode node : nodes) {
                    final NodeId nodeId = nodeDatabaseManager.getNodeId(node);
                    if (nodeId != null) {
                        listBuilder.add(nodeId);
                        nodeMap.put(nodeId, node);
                    }
                }
                nodeIds = listBuilder.build();
            }

            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
            final Map<PendingBlockId, NodeId> downloadPlan = pendingBlockDatabaseManager.selectIncompletePendingBlocks(nodeIds, maximumConcurrentDownloadCount * 2);
            if (downloadPlan.isEmpty()) { return false; }

            for (final PendingBlockId pendingBlockId : downloadPlan.keySet()) {
                if (_currentBlockDownloadSet.size() >= maximumConcurrentDownloadCount) { break; }

                final Sha256Hash blockHash = pendingBlockDatabaseManager.getPendingBlockHash(pendingBlockId);
                if (blockHash == null) { continue; }

                final Boolean itemIsAlreadyBeingDownloaded = _currentBlockDownloadSet.containsKey(blockHash);
                if (itemIsAlreadyBeingDownloaded) { continue; }

                final NodeId nodeId = downloadPlan.get(pendingBlockId);
                final BitcoinNode bitcoinNode = nodeMap.get(nodeId);

                final MilliTimer timer = new MilliTimer();
                _currentBlockDownloadSet.put(blockHash, timer);

                pendingBlockDatabaseManager.updateLastDownloadAttemptTime(pendingBlockId);

                timer.start();
                // _bitcoinNodeManager.requestThinBlock(blockHash, _blockDownloadedCallback);
                bitcoinNode.requestBlock(blockHash, _blockDownloadedCallback);  // TODO: Use NodeManager so NodeHealth is updated and thinBlocks may be used...
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return false;
        }

        return true;
    }

    @Override
    protected void _onSleep() { }

    public BlockDownloader(final BitcoinNodeManager bitcoinNodeManager, final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseCache) {
        _bitcoinNodeManager = bitcoinNodeManager;
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseCache = databaseCache;

        _blockDownloadedCallback = new BitcoinNodeManager.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                    _onBlockDownloaded(block, databaseConnection);
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                    return;
                }
                finally {
                    final Sha256Hash blockHash = block.getHash();

                    final MilliTimer timer = _currentBlockDownloadSet.remove(blockHash);

                    if (timer != null) {
                        timer.stop();
                    }

                    Logger.log("Downloaded Block: " + blockHash + " (" + (timer != null ? timer.getMillisecondsElapsed() : "??") + "ms)");

                    synchronized (_downloadCallbackPin) {
                        _downloadCallbackPin.notifyAll();
                    }
                }

                final Runnable newBlockAvailableCallback = _newBlockAvailableCallback;
                if (newBlockAvailableCallback != null) {
                    newBlockAvailableCallback.run();
                }
            }

            @Override
            public void onFailure(final Sha256Hash blockHash) {
                try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                    final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);

                    final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.getPendingBlockId(blockHash);
                    if (pendingBlockId == null) {
                        Logger.log("Unable to increment download failure count for block: " + blockHash);
                        return;
                    }

                    pendingBlockDatabaseManager.incrementFailedDownloadCount(pendingBlockId);
                    pendingBlockDatabaseManager.purgeFailedPendingBlocks(MAX_DOWNLOAD_FAILURE_COUNT);
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                    Logger.log("Unable to increment download failure count for block: " + blockHash);
                }
                finally {
                    _currentBlockDownloadSet.remove(blockHash);

                    synchronized (_downloadCallbackPin) {
                        _downloadCallbackPin.notifyAll();
                    }
                }
            }
        };
    }

    public void setNewBlockAvailableCallback(final Runnable runnable) {
        _newBlockAvailableCallback = runnable;
    }

}
