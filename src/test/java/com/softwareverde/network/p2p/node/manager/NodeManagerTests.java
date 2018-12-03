package com.softwareverde.network.p2p.node.manager;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.message.type.*;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.test.time.FakeSystemTime;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class NodeManagerTests {
    static class FakeNode extends com.softwareverde.network.p2p.node.Node {
        protected static long _nextNonce = 0L;

        protected Long _lastMessageReceivedTimestamp = 0L;

        public FakeNode(final String host) {
            super(host, 0, BitcoinProtocolMessage.BINARY_PACKET_FORMAT);
        }

        public FakeNode(final String host, final SystemTime systemTime) {
            super(host, 0, BitcoinProtocolMessage.BINARY_PACKET_FORMAT, systemTime);
        }

        @Override
        protected PingMessage _createPingMessage() {
            return new PingMessage() {
                @Override
                public Long getNonce() {
                    return 0L;
                }

                @Override
                public ByteArray getBytes() {
                    return new MutableByteArray(0);
                }
            };
        }

        @Override
        protected PongMessage _createPongMessage(final PingMessage pingMessage) {
            return new PongMessage() {
                @Override
                public Long getNonce() {
                    return 0L;
                }

                @Override
                public ByteArray getBytes() {
                    return new MutableByteArray(0);
                }
            };
        }

        @Override
        protected SynchronizeVersionMessage _createSynchronizeVersionMessage() {
            return new SynchronizeVersionMessage() {
                @Override
                public Long getNonce() {
                    return _nextNonce++;
                }

                @Override
                public NodeIpAddress getLocalNodeIpAddress() {
                    return null;
                }

                @Override
                public Long getTimestamp() {
                    return 0L;
                }

                @Override
                public String getUserAgent() {
                    return "TestAgent";
                }

                @Override
                public ByteArray getBytes() {
                    return new MutableByteArray(0);
                }
            };
        }

        @Override
        protected AcknowledgeVersionMessage _createAcknowledgeVersionMessage(final SynchronizeVersionMessage synchronizeVersionMessage) { return null; }

        @Override
        protected NodeIpAddressMessage _createNodeIpAddressMessage() { return null; }

        @Override
        public Boolean hasActiveConnection() { return true; }

        @Override
        public Long getLastMessageReceivedTimestamp() {
            return _lastMessageReceivedTimestamp;
        }

        @Override
        public Boolean handshakeIsComplete() {
            return true;
        }

        @Override
        protected void _queueMessage(final ProtocolMessage message) {
            super._queueMessage(message);
        }

        public void triggerConnected() {
            _onConnect();
        }

        public void triggerHandshakeComplete() {
            _onAcknowledgeVersionMessageReceived(null);
        }

        public void respondWithPong() {
            _onPongReceived(new PongMessage() {
                @Override
                public Long getNonce() {
                    return 0L;
                }

                @Override
                public ByteArray getBytes() {
                    return new MutableByteArray(0);
                }
            });
        }
    }

    static class FakeNodeFactory implements com.softwareverde.network.p2p.node.NodeFactory<FakeNode> {
        @Override
        public FakeNode newNode(final String host, final Integer port) {
            return new FakeNode(host);
        }
    }

    protected FakeNode[] _setupFakeNodes(final Integer nodeCount, final NodeManager<FakeNode> nodeManager, final Map<NodeId, Integer> nodeSelectedCounts) {
        return _setupFakeNodes(nodeCount, nodeManager, nodeSelectedCounts, new SystemTime());
    }

    protected FakeNode[] _setupFakeNodes(final Integer nodeCount, final NodeManager<FakeNode> nodeManager, final Map<NodeId, Integer> nodeSelectedCounts, SystemTime systemTime) {
        final FakeNode[] nodes = new FakeNode[nodeCount];

        for (int i = 0; i < nodes.length; ++i) {
            final FakeNode fakeNode = new FakeNode(String.valueOf(i), systemTime);
            nodeSelectedCounts.put(fakeNode.getId(), 0);
            nodeManager.addNode(fakeNode);

            fakeNode.triggerConnected();
            fakeNode.triggerHandshakeComplete();

            nodes[i] = fakeNode;
        }

        return nodes;
    }

    @Before
    public void setup() {
        FakeNode._nextNonce = 0L;
        NodeManager._threadPool.start();
    }

    @After
    public void after() {
        // TODO: There are still threads lingering after these tests... this effectively kills them, but ideally this shouldn't be necessary as they should all have been cleaned up.
        NodeManager._threadPool.stop();
    }

    @Test
    public void should_select_different_nodes_for_rapid_requests_that_have_not_returned_yet() {
        // Setup
        final Integer nodeCount = 5;
        final MutableNetworkTime networkTime = new MutableNetworkTime();
        final NodeManager<FakeNode> nodeManager = new NodeManager<FakeNode>(nodeCount, new FakeNodeFactory(), networkTime);
        final HashMap<NodeId, Integer> nodeSelectedCounts = new HashMap<NodeId, Integer>();
        final HashMap<NodeManager.NodeApiRequest<FakeNode>, FakeNode> invocationCallbacks = new HashMap<NodeManager.NodeApiRequest<FakeNode>, FakeNode>(nodeCount);

        _setupFakeNodes(nodeCount, nodeManager, nodeSelectedCounts);

        final NodeManager.NodeApiRequest<FakeNode> nodeApiInvocation = new NodeManager.NodeApiRequest<FakeNode>() {
            @Override
            public void run(final FakeNode selectedNode) {
                // NOTE: NodeApiInvocationCallback.didTimeout registers the response was received. It is intentionally not called until after all the requests have been issued.
                invocationCallbacks.put(this, selectedNode);

                final NodeId nodeId = selectedNode.getId();
                nodeSelectedCounts.put(nodeId, (nodeSelectedCounts.get(nodeId) + 1));
            }

            @Override
            public void onFailure() {

            }
        };

        // Action
        for (int i = 0; i < nodeCount; ++i) {
            nodeManager.executeRequest(nodeApiInvocation);
        }

        // Cleanup
        for (final NodeManager.NodeApiRequest<FakeNode> apiRequest : invocationCallbacks.keySet()) {
            final FakeNode selectedNode = invocationCallbacks.get(apiRequest);
            nodeManager._onResponseReceived(selectedNode, apiRequest); // Mark the requests as received...
        }
        nodeManager._pendingRequestsManager.stop();

        // Assert
        for (final NodeId nodeId : nodeSelectedCounts.keySet()) {
            final Integer selectCount = nodeSelectedCounts.get(nodeId);
            Assert.assertEquals(1, selectCount.intValue());
        }
    }

    @Test
    public void should_select_different_nodes_for_slow_responses() {
        // Setup
        final FakeSystemTime fakeSystemTime = new FakeSystemTime();

        final Integer nodeCount = 5;
        final MutableNetworkTime networkTime = new MutableNetworkTime();
        final NodeManager<FakeNode> nodeManager = new NodeManager<FakeNode>(nodeCount, new FakeNodeFactory(), networkTime, fakeSystemTime);
        final HashMap<NodeId, Integer> nodeSelectedCounts = new HashMap<NodeId, Integer>();
        final HashMap<NodeId, NodeManager.NodeApiRequest<FakeNode>> invocationCallbacks = new HashMap<NodeId, NodeManager.NodeApiRequest<FakeNode>>(nodeCount);

        final FakeNode[] nodes = _setupFakeNodes(nodeCount, nodeManager, nodeSelectedCounts);

        final FakeNode designatedSlowNode = nodes[2];
        final NodeId designatedSlowNodeId = designatedSlowNode.getId();

        // Action
        for (int i = 0; i < nodeCount; ++i) {
            final NodeManager.NodeApiRequest<FakeNode> apiRequest = new NodeManager.NodeApiRequest<FakeNode>() {
                @Override
                public void run(final FakeNode selectedNode) {
                    Assert.assertNull(invocationCallbacks.get(selectedNode.getId()));

                    // NOTE: NodeApiInvocationCallback.didTimeout registers the response was received. It is intentionally not called until after all the requests have been issued.
                    invocationCallbacks.put(selectedNode.getId(), this);

                    final NodeId nodeId = selectedNode.getId();
                    nodeSelectedCounts.put(nodeId, (nodeSelectedCounts.get(nodeId) + 1));
                }

                @Override
                public void onFailure() { }
            };

            nodeManager.executeRequest(apiRequest);
        }

        for (int i = 0; i < nodeCount; ++i) {
            final NodeId nodeId = nodes[i].getId();
            if (Util.areEqual(designatedSlowNodeId, nodeId)) { continue; }

            fakeSystemTime.advanceTimeInMilliseconds(100L);
            final NodeManager.NodeApiRequest<FakeNode> apiRequest = invocationCallbacks.get(nodeId);
            nodeManager._onResponseReceived(nodes[i], apiRequest); // Mark the requests as received...
        }
        { // Emulate a very slow response for the designated request...
            fakeSystemTime.advanceTimeInMilliseconds(20000L);
            final NodeManager.NodeApiRequest<FakeNode> apiRequest = invocationCallbacks.get(designatedSlowNodeId);
            nodeManager._onResponseReceived(designatedSlowNode, apiRequest); // Mark the requests as received...
        }
        invocationCallbacks.clear();

        for (int i = 0; i < (nodeCount - 1); ++i) {
            final NodeManager.NodeApiRequest<FakeNode> apiRequest = new NodeManager.NodeApiRequest<FakeNode>() {
                @Override
                public void run(final FakeNode selectedNode) {
                    invocationCallbacks.put(selectedNode.getId(), this);

                    final NodeId nodeId = selectedNode.getId();
                    nodeSelectedCounts.put(nodeId, (nodeSelectedCounts.get(nodeId) + 1));
                }

                @Override
                public void onFailure() { }
            };

            nodeManager.executeRequest(apiRequest);
        }

        // Cleanup
        for (int i = 0; i < nodeCount; ++i) {
            final NodeId nodeId = nodes[i].getId();

            fakeSystemTime.advanceTimeInMilliseconds(100L);
            final NodeManager.NodeApiRequest<FakeNode> apiRequest = invocationCallbacks.get(nodeId);
            if (apiRequest != null) {
                nodeManager._onResponseReceived(nodes[i], apiRequest); // Mark the requests as received...
            }
        }
        nodeManager._pendingRequestsManager.stop();

        // Assert
        for (final NodeId nodeId : nodeSelectedCounts.keySet()) {
            final Integer selectCount = nodeSelectedCounts.get(nodeId);
            if (Util.areEqual(designatedSlowNodeId, nodeId)) {
                Assert.assertEquals(1, selectCount.intValue()); // The slowest node should not have been issued a second request...
            }
            else {
                Assert.assertEquals(2, selectCount.intValue());
            }
        }
    }

    @Test
    public void should_select_different_nodes_for_slow_responses_2() {
        // Setup
        final FakeSystemTime fakeSystemTime = new FakeSystemTime();

        final Integer nodeCount = 5;
        final MutableNetworkTime networkTime = new MutableNetworkTime();
        final NodeManager<FakeNode> nodeManager = new NodeManager<FakeNode>(nodeCount, new FakeNodeFactory(), networkTime, fakeSystemTime);
        final HashMap<NodeId, Integer> nodeSelectedCounts = new HashMap<NodeId, Integer>();
        final HashMap<NodeManager.NodeApiRequest<FakeNode>, FakeNode> invocationCallbacks = new HashMap<NodeManager.NodeApiRequest<FakeNode>, FakeNode>(nodeCount);

        final FakeNode[] fakeNodes = _setupFakeNodes(nodeCount, nodeManager, nodeSelectedCounts);

        final FakeNode designatedSlowNode = fakeNodes[1];
        final Long designatedSlowNodeId = designatedSlowNode.getId().longValue();

        final HashMap<FakeNode, NodeManager.NodeApiRequest<FakeNode>> apiRequestMap = new HashMap<FakeNode, NodeManager.NodeApiRequest<FakeNode>>();

        // Action
        for (int i = 0; i < nodeCount; ++i) {
            final NodeManager.NodeApiRequest<FakeNode> apiRequest = new NodeManager.NodeApiRequest<FakeNode>() {
                @Override
                public void run(final FakeNode selectedNode) {
                    Assert.assertNull(apiRequestMap.get(selectedNode));
                    apiRequestMap.put(selectedNode, this);

                    // NOTE: NodeApiInvocationCallback.didTimeout registers the response was received. It is intentionally not called until after all the requests have been issued.
                    invocationCallbacks.put(this, selectedNode);

                    final NodeId nodeId = selectedNode.getId();
                    nodeSelectedCounts.put(nodeId, (nodeSelectedCounts.get(nodeId) + 1));
                }

                @Override
                public void onFailure() { }
            };

            nodeManager.executeRequest(apiRequest);
        }

        for (int i = 0; i < nodeCount; ++i) {
            if (Util.areEqual(designatedSlowNodeId, i)) { continue; }

            fakeSystemTime.advanceTimeInMilliseconds(100L);
            final NodeManager.NodeApiRequest<FakeNode> apiInvocationCallback = apiRequestMap.get(fakeNodes[i]);
            nodeManager._onResponseReceived(fakeNodes[i], apiInvocationCallback); // Mark the requests as received...
        }
        { // Emulate a very slow response for the designated request...
            fakeSystemTime.advanceTimeInMilliseconds(20000L);
            final NodeManager.NodeApiRequest<FakeNode> apiInvocationCallback = apiRequestMap.get(designatedSlowNode);
            nodeManager._onResponseReceived(designatedSlowNode, apiInvocationCallback); // Mark the requests as received...
        }
        invocationCallbacks.clear();
        apiRequestMap.clear();

        fakeSystemTime.advanceTimeInMilliseconds(30000L); // "Wait" for some time in the future...

        for (int i = 0; i < (nodeCount - 1); ++i) {
            final NodeManager.NodeApiRequest<FakeNode> apiRequest = new NodeManager.NodeApiRequest<FakeNode>() {
                @Override
                public void run(final FakeNode selectedNode) {
                    // NOTE: NodeApiInvocationCallback.didTimeout registers the response was received. It is intentionally not called until after all the requests have been issued.
                    invocationCallbacks.put(this, selectedNode);

                    final NodeId nodeId = selectedNode.getId();
                    nodeSelectedCounts.put(nodeId, (nodeSelectedCounts.get(nodeId) + 1));
                }

                @Override
                public void onFailure() { }
            };

            nodeManager.executeRequest(apiRequest);
        }

        // Cleanup
        for (final NodeManager.NodeApiRequest<FakeNode> apiRequest : invocationCallbacks.keySet()) {
            final FakeNode fakeNode = invocationCallbacks.get(apiRequest);
            nodeManager._onResponseReceived(fakeNode, apiRequest); // Mark the requests as received...
        }
        nodeManager._pendingRequestsManager.stop();

        // Assert
        for (final NodeId nodeId : nodeSelectedCounts.keySet()) {
            System.out.println("Node " + nodeId + " Health: " + nodeManager.getNodeHealth(nodeId));
            final Integer selectCount = nodeSelectedCounts.get(nodeId);
            if (Util.areEqual(NodeId.wrap(designatedSlowNodeId), nodeId)) {
                Assert.assertEquals(1, selectCount.intValue()); // The slowest node should not have been issued a second request...
            }
            else {
                Assert.assertEquals(2, selectCount.intValue());
            }
        }
    }

    @Test
    public void should_prefer_nodes_with_lowest_ping() {
        // Setup
        final FakeSystemTime fakeSystemTime = new FakeSystemTime();

        final Integer nodeCount = 5;
        final MutableNetworkTime networkTime = new MutableNetworkTime();
        final NodeManager<FakeNode> nodeManager = new NodeManager<FakeNode>(nodeCount, new FakeNodeFactory(), networkTime, fakeSystemTime);
        final HashMap<NodeId, Integer> nodeSelectedCounts = new HashMap<NodeId, Integer>();
        final HashMap<NodeManager.NodeApiRequest<FakeNode>, FakeNode> invocationCallbacks = new HashMap<NodeManager.NodeApiRequest<FakeNode>, FakeNode>(nodeCount);

        final FakeNode[] nodes = _setupFakeNodes(nodeCount, nodeManager, nodeSelectedCounts, fakeSystemTime);

        {  // Mark nodes as idle...
            nodes[0]._lastMessageReceivedTimestamp = -60000L;
            nodes[1]._lastMessageReceivedTimestamp = -60000L;
            nodes[2]._lastMessageReceivedTimestamp = -60000L;
            nodes[3]._lastMessageReceivedTimestamp = -60000L;
            nodes[4]._lastMessageReceivedTimestamp = -60000L;
        }

        { // Set node pings... From Best To Worst: 3, 2, 4, 0, 1
            nodeManager._pingIdleNodes();

            fakeSystemTime.advanceTimeInMilliseconds(100L);
            nodes[3].respondWithPong();
            fakeSystemTime.advanceTimeInMilliseconds(100L);
            nodes[2].respondWithPong();
            fakeSystemTime.advanceTimeInMilliseconds(100L);
            nodes[4].respondWithPong();
            fakeSystemTime.advanceTimeInMilliseconds(100L);
            nodes[0].respondWithPong();
            fakeSystemTime.advanceTimeInMilliseconds(100L);
            nodes[1].respondWithPong();
        }

        final NodeId[] selectedNodesOrder = new NodeId[5];
        final Container<Integer> selectedNodeOrderIndex = new Container<Integer>(0);

        // Action
        for (int i = 0; i < nodeCount; ++i) {
            final NodeManager.NodeApiRequest<FakeNode> nodeApiInvocation = new NodeManager.NodeApiRequest<FakeNode>() {
                @Override
                public void run(final FakeNode selectedNode) {
                    // NOTE: NodeApiInvocationCallback.didTimeout registers the response was received. It is intentionally not called until after all the requests have been issued.
                    invocationCallbacks.put(this, selectedNode);

                    final NodeId nodeId = selectedNode.getId();
                    selectedNodesOrder[selectedNodeOrderIndex.value] = nodeId;
                    selectedNodeOrderIndex.value += 1;
                }

                @Override
                public void onFailure() { }
            };

            nodeManager.executeRequest(nodeApiInvocation);
        }

        // Cleanup
        for (final NodeManager.NodeApiRequest<FakeNode> apiRequest : invocationCallbacks.keySet()) {
            final FakeNode fakeNode = invocationCallbacks.get(apiRequest);
            nodeManager._onResponseReceived(fakeNode, apiRequest); // Mark the requests as received...
        }
        nodeManager._pendingRequestsManager.stop();

        // Assert
        Assert.assertEquals(nodes[3].getId(), selectedNodesOrder[0]);
        Assert.assertEquals(nodes[2].getId(), selectedNodesOrder[1]);
        Assert.assertEquals(nodes[4].getId(), selectedNodesOrder[2]);
        Assert.assertEquals(nodes[0].getId(), selectedNodesOrder[3]);
        Assert.assertEquals(nodes[1].getId(), selectedNodesOrder[4]);
    }
}
