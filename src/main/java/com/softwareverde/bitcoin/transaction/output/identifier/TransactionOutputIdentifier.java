package com.softwareverde.bitcoin.transaction.output.identifier;

import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.Const;
import com.softwareverde.util.Util;

public class TransactionOutputIdentifier implements Const {
    public static TransactionOutputIdentifier fromTransactionInput(final TransactionInput transactionInput) {
        return new TransactionOutputIdentifier(transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
    }

    protected final Sha256Hash _transactionHash;
    protected final Integer _outputIndex;

    public TransactionOutputIdentifier(final Sha256Hash transactionHash, final Integer outputIndex) {
        _transactionHash = transactionHash.asConst();
        _outputIndex = outputIndex;
    }

    public Sha256Hash getTransactionHash() {
        return _transactionHash;
    }

    public Integer getOutputIndex() {
        return _outputIndex;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof TransactionOutputIdentifier)) { return false; }
        final TransactionOutputIdentifier transactionOutputIdentifier = (TransactionOutputIdentifier) object;

        if (! Util.areEqual(_transactionHash, transactionOutputIdentifier._transactionHash)) { return false; }
        if (! Util.areEqual(_outputIndex, transactionOutputIdentifier._outputIndex)) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        return (_transactionHash.hashCode() + _outputIndex.hashCode());
    }
}
