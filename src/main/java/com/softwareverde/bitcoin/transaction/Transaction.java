package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.block.merkleroot.Hashable;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.list.List;
import com.softwareverde.json.Jsonable;

public interface Transaction extends Hashable, Constable<ImmutableTransaction>, Jsonable {
    Long VERSION = 0x01L;
    Long SATOSHIS_PER_BITCOIN = 100_000_000L;

    static Transaction createCoinbaseTransaction(final Long blockHeight, final String coinbaseMessage, final Address address, final Long satoshis) {
        final MutableTransaction coinbaseTransaction = new MutableTransaction();
        coinbaseTransaction.addTransactionInput(TransactionInput.createCoinbaseTransactionInput(blockHeight, coinbaseMessage));
        coinbaseTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address, satoshis));
        return coinbaseTransaction;
    }

    static Transaction createCoinbaseTransactionWithExtraNonce(final Long blockHeight, final String coinbaseMessage, final Integer extraNonceByteCount, final Address address, final Long satoshis) {
        final MutableTransaction coinbaseTransaction = new MutableTransaction();
        coinbaseTransaction.addTransactionInput(TransactionInput.createCoinbaseTransactionInputWithExtraNonce(blockHeight, coinbaseMessage, extraNonceByteCount));
        coinbaseTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address, satoshis));
        return coinbaseTransaction;
    }

    Long getVersion();
    List<TransactionInput> getTransactionInputs();
    List<TransactionOutput> getTransactionOutputs();
    LockTime getLockTime();
    Long getTotalOutputValue();

    @Override
    ImmutableTransaction asConst();
}
