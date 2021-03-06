package com.softwareverde.bitcoin.transaction.script.runner.context;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.list.List;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

public class MutableContext implements Context, Const {
    public static MutableContext getContextForVerification(final Transaction signedTransaction, final Integer transactionInputIndex, final TransactionOutput transactionOutputBeingSpent) {
        return MutableContext.getContextForVerification(signedTransaction, transactionInputIndex, transactionOutputBeingSpent, MedianBlockTime.MAX_VALUE);
    }

    public static MutableContext getContextForVerification(final Transaction signedTransaction, final Integer transactionInputIndex, final TransactionOutput transactionOutputBeingSpent, final MedianBlockTime medianBlockTime) {
        final List<TransactionInput> signedTransactionInputs = signedTransaction.getTransactionInputs();
        final TransactionInput signedTransactionInput = signedTransactionInputs.get(transactionInputIndex);

        final MutableContext mutableContext = new MutableContext();
        mutableContext.setCurrentScript(null);
        mutableContext.setTransactionInputIndex(transactionInputIndex);
        mutableContext.setTransactionInput(signedTransactionInput);
        mutableContext.setTransaction(signedTransaction);
        mutableContext.setBlockHeight(Long.MAX_VALUE);
        mutableContext.setMedianBlockTime(Util.coalesce(medianBlockTime, MedianBlockTime.MAX_VALUE));
        mutableContext.setTransactionOutputBeingSpent(transactionOutputBeingSpent);
        mutableContext.setCurrentScriptLastCodeSeparatorIndex(0);
        return mutableContext;
    }

    protected Long _blockHeight;
    protected MedianBlockTime _medianBlockTime;
    protected Transaction _transaction;

    protected Integer _transactionInputIndex;
    protected TransactionInput _transactionInput;
    protected TransactionOutput _transactionOutput;

    protected Script _currentScript = null;
    protected Integer _currentScriptIndex = 0;
    protected Integer _scriptLastCodeSeparatorIndex = 0;

    public MutableContext() { }

    public MutableContext(final Context context) {
        _blockHeight = context.getBlockHeight();
        _medianBlockTime = context.getMedianBlockTime();
        _transaction = ConstUtil.asConstOrNull(context.getTransaction());
        _transactionInputIndex = context.getTransactionInputIndex();
        _transactionInput = ConstUtil.asConstOrNull(context.getTransactionInput());
        _transactionOutput = ConstUtil.asConstOrNull(context.getTransactionOutput());

        final Script currentScript = context.getCurrentScript();
        _currentScript = ConstUtil.asConstOrNull(currentScript);
        _currentScriptIndex = context.getScriptIndex();
        _scriptLastCodeSeparatorIndex = context.getScriptLastCodeSeparatorIndex();
    }

    public void setBlockHeight(final Long blockHeight) {
        _blockHeight = blockHeight;
    }

    public void setMedianBlockTime(final MedianBlockTime medianBlockTime) {
        _medianBlockTime = medianBlockTime;
    }

    /**
     * Sets the Transaction currently being validated.
     */
    public void setTransaction(final Transaction transaction) {
        _transaction = transaction;
    }

    public void setTransactionInputIndex(final Integer transactionInputIndex) {
        _transactionInputIndex = transactionInputIndex;
    }

    public void setTransactionInput(final TransactionInput transactionInput) {
        _transactionInput = transactionInput;
    }

    public void setTransactionOutputBeingSpent(final TransactionOutput transactionOutput) {
        _transactionOutput = transactionOutput;
    }

    public void setCurrentScript(final Script script) {
        _currentScript = script;
        _currentScriptIndex = 0;
        _scriptLastCodeSeparatorIndex = 0;
    }

    public void incrementCurrentScriptIndex() {
        _currentScriptIndex += 1;
    }

    public void setCurrentScriptLastCodeSeparatorIndex(final Integer codeSeparatorIndex) {
        _scriptLastCodeSeparatorIndex = codeSeparatorIndex;
    }

    @Override
    public Long getBlockHeight() {
        return _blockHeight;
    }

    @Override
    public MedianBlockTime getMedianBlockTime() {
        return _medianBlockTime;
    }

    @Override
    public TransactionInput getTransactionInput() {
        return _transactionInput;
    }

    @Override
    public TransactionOutput getTransactionOutput() {
        return _transactionOutput;
    }

    @Override
    public Transaction getTransaction() {
        return _transaction;
    }

    @Override
    public Integer getTransactionInputIndex() {
        return _transactionInputIndex;
    }

    @Override
    public Script getCurrentScript() {
        return _currentScript;
    }

    @Override
    public Integer getScriptIndex() {
        return _currentScriptIndex;
    }

    @Override
    public Integer getScriptLastCodeSeparatorIndex() {
        return _scriptLastCodeSeparatorIndex;
    }

    @Override
    public ImmutableContext asConst() {
        return new ImmutableContext(this);
    }

    @Override
    public Json toJson() {
        final ContextDeflater contextDeflater = new ContextDeflater();
        return contextDeflater.toJson(this);
    }
}
