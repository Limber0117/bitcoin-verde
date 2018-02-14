package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class BlockHeaderInflater {
    protected BlockHeader _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();
        final BlockHeader blockHeader = new BlockHeader();

        // 0100 0000                                                                        // Version
        // 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000  // Previous Block Hash
        // 3BA3 EDFD 7A7B 12B2 7AC7 2C3E 6776 8F61 7FC8 1BC3 888A 5132 3A9F B8AA 4B1E 5E4A  // Merkle Root
        // 29AB 5F49                                                                        // Timestamp
        // FFFF 001D                                                                        // Difficulty
        // 1DAC 2B7C                                                                        // Nonce
        // 0101 0000 0001 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 FFFF FFFF 4D04 FFFF 001D 0104 4554 6865 2054 696D 6573 2030 332F 4A61 6E2F 3230 3039 2043 6861 6E63 656C 6C6F 7220 6F6E 2062 7269 6E6B 206F 6620 7365 636F 6E64 2062 6169 6C6F 7574 2066 6F72 2062 616E 6B73 FFFF FFFF 0100 F205 2A01 0000 0043 4104 678A FDB0 FE55 4827 1967 F1A6 7130 B710 5CD6 A828 E039 09A6 7962 E0EA 1F61 DEB6 49F6 BC3F 4CEF 38C4 F355 04E5 1EC1 12DE 5C38 4DF7 BA0B 8D57 8A4C 702B 6BF1 1D5F AC00 0000 00

        blockHeader._version = byteArrayReader.readInteger(4, Endian.LITTLE);
        ByteUtil.setBytes(blockHeader._previousBlockHash, byteArrayReader.readBytes(32, Endian.LITTLE));
        ByteUtil.setBytes(blockHeader._merkleRoot, byteArrayReader.readBytes(32, Endian.LITTLE));
        blockHeader._timestamp = byteArrayReader.readLong(4, Endian.LITTLE);

        final byte[] difficultyBytes = byteArrayReader.readBytes(4, Endian.BIG);
        blockHeader._difficulty = difficultyEncoder.decodeDifficulty(difficultyBytes);
        blockHeader._nonce = byteArrayReader.readLong(4, Endian.LITTLE);
        // blockHeader._transactionCount = byteArrayReader.readVariableSizedInteger().intValue(); // Always 0 for Block Headers...

        return blockHeader;
    }

    public BlockHeader fromBytes(final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(byteArrayReader);
    }

    public BlockHeader fromBytes(final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }
}
