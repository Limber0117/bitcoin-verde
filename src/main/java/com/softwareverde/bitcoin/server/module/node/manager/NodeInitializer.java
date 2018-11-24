package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.network.socket.BinarySocket;

public class NodeInitializer {
    public interface TransactionsAnnouncementCallbackFactory {
        BitcoinNode.TransactionInventoryMessageCallback createTransactionsAnnouncementCallback(BitcoinNode bitcoinNode);
    }

    protected final SynchronizationStatus _synchronizationStatus;
    protected final BitcoinNode.BlockInventoryMessageCallback _blockInventoryMessageHandler;
    protected final TransactionsAnnouncementCallbackFactory _transactionsAnnouncementCallbackFactory;
    protected final BitcoinNode.QueryBlocksCallback _queryBlocksCallback;
    protected final BitcoinNode.QueryBlockHeadersCallback _queryBlockHeadersCallback;
    protected final BitcoinNode.RequestDataCallback _requestDataCallback;

    protected void _initializeNode(final BitcoinNode bitcoinNode) {
        bitcoinNode.setSynchronizationStatusHandler(_synchronizationStatus);

        bitcoinNode.setQueryBlocksCallback(_queryBlocksCallback);
        bitcoinNode.setQueryBlockHeadersCallback(_queryBlockHeadersCallback);
        bitcoinNode.setRequestDataCallback(_requestDataCallback);

        bitcoinNode.setBlockInventoryMessageHandler(_blockInventoryMessageHandler);

        final BitcoinNode.TransactionInventoryMessageCallback transactionsAnnouncementCallback = _transactionsAnnouncementCallbackFactory.createTransactionsAnnouncementCallback(bitcoinNode);
        bitcoinNode.setTransactionsAnnouncementCallback(transactionsAnnouncementCallback);
    }

    public NodeInitializer(final SynchronizationStatus synchronizationStatus, final BitcoinNode.BlockInventoryMessageCallback blockInventoryMessageHandler, final TransactionsAnnouncementCallbackFactory transactionsAnnouncementCallbackFactory, final BitcoinNode.QueryBlocksCallback queryBlocksCallback, final BitcoinNode.QueryBlockHeadersCallback queryBlockHeadersCallback, final BitcoinNode.RequestDataCallback requestDataCallback) {
        _synchronizationStatus = synchronizationStatus;
        _blockInventoryMessageHandler = blockInventoryMessageHandler;
        _transactionsAnnouncementCallbackFactory = transactionsAnnouncementCallbackFactory;
        _queryBlocksCallback = queryBlocksCallback;
        _queryBlockHeadersCallback = queryBlockHeadersCallback;
        _requestDataCallback = requestDataCallback;
    }

    public BitcoinNode initializeNode(final String host, final Integer port) {
        final BitcoinNode node = new BitcoinNode(host, port);
        _initializeNode(node);
        return node;
    }

    public BitcoinNode initializeNode(final BinarySocket binarySocket) {
        final BitcoinNode node = new BitcoinNode(binarySocket);
        _initializeNode(node);
        return node;
    }

    public void initializeNode(final BitcoinNode bitcoinNode) {
        _initializeNode(bitcoinNode);
    }
}
