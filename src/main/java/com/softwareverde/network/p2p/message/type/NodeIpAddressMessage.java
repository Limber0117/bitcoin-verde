package com.softwareverde.network.p2p.message.type;

import com.softwareverde.constable.list.List;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;

public interface NodeIpAddressMessage extends ProtocolMessage {
    List<NodeIpAddress> getNodeIpAddresses();
    void addAddress(NodeIpAddress nodeIpAddress);
}
