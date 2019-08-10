package com.softwareverde.bitcoin.block.header.difficulty;

import com.softwareverde.bitcoin.block.header.difficulty.work.BlockWork;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

import java.math.BigDecimal;
import java.math.BigInteger;

public class ImmutableDifficulty implements Difficulty, Const {
    protected static BigInteger MAX_WORK = BigInteger.valueOf(2L).pow(256);

    private final Integer _exponent;
    private final byte[] _significand = new byte[3];

    private ByteArray _cachedBytes = null;

    protected void _requireCachedBytes() {
        if (_cachedBytes == null) {
            _cachedBytes = _convertToBytes();
        }
    }

    protected BigInteger _toBigInteger() {
        return new BigInteger(_convertToBytes().unwrap());
    }

    protected BigDecimal _toBigDecimal() {
        final BigInteger bigInteger = _toBigInteger();
        final BigDecimal bigDecimal = new BigDecimal(bigInteger);
        // NOTE: Invoking the BigDecimal constructor with the scale provided is NOT the same as setting its scale afterwards.
        //  Therefore, think twice before changing/condensing this.
        //  The BigDecimal(BigInteger, Scale) constructor sets the value to 10^Scale less than what is perceived.
        return bigDecimal.setScale(4, BigDecimal.ROUND_UNNECESSARY); // setScale(4);
    }

    protected ByteArray _encode() {
        final byte[] bytes = new byte[4];
        bytes[0] = (byte) (_exponent + 3);
        ByteUtil.setBytes(bytes, _significand, 1);
        return MutableByteArray.wrap(bytes);
    }

    public ImmutableDifficulty(final byte[] significand, final Integer exponent) {
        _exponent = exponent;

        final int copyCount = Math.min(_significand.length, significand.length);
        for (int i = 0; i < copyCount; ++i) {
            _significand[(_significand.length - i) - 1] = significand[(significand.length - i) - 1];
        }
    }

    public ImmutableDifficulty(final Difficulty difficulty) {
        _exponent = difficulty.getExponent();
        ByteUtil.setBytes(_significand, difficulty.getSignificand());
    }

    protected MutableByteArray _convertToBytes() {
        final byte[] bytes = new byte[32];
        ByteUtil.setBytes(bytes, _significand, (32 - _exponent - _significand.length));
        return MutableByteArray.wrap(bytes);
    }

    @Override
    public ByteArray getBytes() {
        return _convertToBytes();
    }

    @Override
    public ByteArray encode() {
        return _encode();
    }

    @Override
    public Integer getExponent() { return _exponent; }

    @Override
    public byte[] getSignificand() { return ByteUtil.copyBytes(_significand); }

    @Override
    public Boolean isSatisfiedBy(final Sha256Hash hash) {
        _requireCachedBytes();

        for (int i = 0; i < _cachedBytes.getByteCount(); ++i) {
            final int difficultyByte = ByteUtil.byteToInteger(_cachedBytes.getByte(i));
            final int sha256Byte = ByteUtil.byteToInteger(hash.getByte(i));
            if (sha256Byte == difficultyByte) { continue; }
            return (sha256Byte < difficultyByte);
        }

        return true;
    }

    @Override
    public Boolean isLessDifficultThan(final Difficulty difficulty) {
        _requireCachedBytes();

        final ByteArray difficultyBytes0 = _cachedBytes;
        final ByteArray difficultyBytes1 = difficulty.getBytes();

        for (int i = 0; i < difficultyBytes0.getByteCount(); ++i) {
            final int difficultyByteAsInteger0 = ByteUtil.byteToInteger(difficultyBytes0.getByte(i));
            final int difficultyByteAsInteger1 = ByteUtil.byteToInteger(difficultyBytes1.getByte(i));
            if (difficultyByteAsInteger0 == difficultyByteAsInteger1) { continue; }

            // NOTE: Greater values represent less difficulty.
            return (difficultyByteAsInteger0 > difficultyByteAsInteger1);
        }

        return false;
    }

    @Override
    public BigDecimal getDifficultyRatio() {
        final BigDecimal currentValue = _toBigDecimal();
        final BigDecimal baseDifficultyValue = Difficulty.BASE_DIFFICULTY._toBigDecimal();
        return baseDifficultyValue.divide(currentValue, BigDecimal.ROUND_HALF_UP);
    }

    @Override
    public Difficulty multiplyBy(final double difficultyAdjustment) {
        final BigDecimal currentValue = _toBigDecimal();
        final BigDecimal bigDecimal = currentValue.multiply(BigDecimal.valueOf(difficultyAdjustment));
        return Difficulty.fromBigInteger(bigDecimal.toBigInteger());
    }

    @Override
    public Difficulty divideBy(final double difficultyAdjustment) {
        final BigDecimal currentValue = _toBigDecimal();
        final BigDecimal bigDecimal = currentValue.divide(BigDecimal.valueOf(difficultyAdjustment), BigDecimal.ROUND_HALF_UP);
        return Difficulty.fromBigInteger(bigDecimal.toBigInteger());
    }

    @Override
    public BlockWork calculateWork() {
        final BigInteger difficultyBigInteger = _toBigInteger();
        final BigInteger proofOfWorkBigInteger = MAX_WORK.divide(difficultyBigInteger.add(BigInteger.ONE));
        final byte[] workBytes = proofOfWorkBigInteger.toByteArray();

        final MutableByteArray workByteArray = new MutableByteArray(32);
        for (int i = 0; i < workBytes.length; ++i) {
            final byte b = workBytes[workBytes.length - i - 1];
            workByteArray.set(workByteArray.getByteCount() - i - 1, b);
        }

        return BlockWork.fromByteArray(workByteArray);
    }

    @Override
    public ImmutableDifficulty asConst() {
        return this;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof Difficulty)) { return false; }

        final Difficulty difficulty = (Difficulty) object;
        if (! _exponent.equals(difficulty.getExponent())) { return false; }

        return ByteUtil.areEqual(_significand, difficulty.getSignificand());
    }

    @Override
    public String toString() {
        return _encode().toString();
    }
}
