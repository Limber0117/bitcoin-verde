package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableSequenceNumber;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.unlocking.ImmutableUnlockingScript;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.bytearray.Endian;

public class TransactionInputInflater {
    protected MutableTransactionInput _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final MutableTransactionInput transactionInput = new MutableTransactionInput();

        transactionInput._previousOutputTransactionHash = MutableSha256Hash.wrap(byteArrayReader.readBytes(32, Endian.LITTLE));
        transactionInput._previousOutputIndex = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Integer scriptByteCount = byteArrayReader.readVariableSizedInteger().intValue();
        if ( (scriptByteCount > Script.MAX_BYTE_COUNT) || (scriptByteCount < 0)) { return null; }
        transactionInput._unlockingScript = new ImmutableUnlockingScript(MutableByteArray.wrap(byteArrayReader.readBytes(scriptByteCount, Endian.BIG)));
        transactionInput._sequenceNumber = new ImmutableSequenceNumber(byteArrayReader.readLong(4, Endian.LITTLE));

        if (byteArrayReader.didOverflow()) { return null; }

        return transactionInput;
    }

    public void _debugBytes(final ByteArrayReader byteArrayReader) {
        Logger.log("Tx Input: Prev Tx: " + MutableByteArray.wrap(byteArrayReader.readBytes(32)));
        Logger.log("Tx Input: Prev Out Ix: " + MutableByteArray.wrap(byteArrayReader.readBytes(4)));

        final ByteArrayReader.VariableSizedInteger variableSizedInteger = byteArrayReader.peakVariableSizedInteger();
        Logger.log("Tx Input: Script Byte Count: " + HexUtil.toHexString(byteArrayReader.readBytes(variableSizedInteger.bytesConsumedCount)));
        Logger.log("Tx Input: Script: " + HexUtil.toHexString(byteArrayReader.readBytes((int) variableSizedInteger.value)));
        Logger.log("Tx Input: Sequence Number: " + HexUtil.toHexString(byteArrayReader.readBytes(4)));
    }

    public MutableTransactionInput fromBytes(final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(byteArrayReader);
    }

    public MutableTransactionInput fromBytes(final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }
}
