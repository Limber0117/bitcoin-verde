package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.bip.HF20181115SV;
import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.io.Logger;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class ArithmeticOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_ARITHMETIC;

    protected static ArithmeticOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new ArithmeticOperation(opcodeByte, opcode);
    }

    protected ArithmeticOperation(final byte value, final Opcode opcode) {
        super(value, TYPE, opcode);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableContext context) {
        // Meh.
        if (! _opcode.isEnabled()) {
            Logger.log("NOTICE: Opcode is disabled: " + _opcode);
            return false;
        }

        switch (_opcode) {
            case ADD_ONE: {
                final Value value = stack.pop();
                final Long intValue = value.asLong();

                final Long newIntValue = (intValue + 1L);
                if (_didIntegerOverflow(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case SUBTRACT_ONE: {
                final Value value = stack.pop();
                final Long intValue = value.asLong();

                final Long newIntValue = (intValue - 1L);
                if (_didIntegerOverflow(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case MULTIPLY_BY_TWO: {
                final Value value = stack.pop();
                final Long intValue = value.asLong();

                final Long newIntValue = (intValue * 2L);
                if (_didIntegerOverflow(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case DIVIDE_BY_TWO: {
                final Value value = stack.pop();
                final Long intValue = value.asLong();

                final Long newIntValue = (intValue / 2L);
                if (_didIntegerOverflow(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case NEGATE: {
                final Value value = stack.pop();
                final Long intValue = value.asLong();

                final Long newIntValue = (-intValue);
                if (_didIntegerOverflow(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case ABSOLUTE_VALUE: {
                final Value value = stack.pop();
                final Long intValue = value.asLong();

                final Long newIntValue = Math.abs(intValue);
                if (_didIntegerOverflow(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case NOT: {
                final Value value = stack.pop();
                final Integer intValue = value.asInteger();

                final Long newIntValue = (intValue == 0 ? 1L : 0L);

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case ADD: {
                final Value value1 = stack.pop();
                final Value value0 = stack.pop();

                final Long intValue0 = value0.asLong();
                final Long intValue1 = value1.asLong();

                final Long newIntValue = (intValue0 + intValue1);
                if (_didIntegerOverflow(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case SUBTRACT: {
                final Value value1 = stack.pop();
                final Value value0 = stack.pop();

                final Long intValue0 = value0.asLong();
                final Long intValue1 = value1.asLong();

                final Long newIntValue = (intValue0 - intValue1);
                if (_didIntegerOverflow(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case MULTIPLY: {
                if (! HF20181115SV.isEnabled(context.getBlockHeight())) { // OP_MUL is enabled on the Bitcoin SV fork...
                    Logger.log("NOTICE: Opcode is disabled: " + _opcode);
                    return false;
                }

                final Value value1 = stack.pop();
                final Value value0 = stack.pop();

                final Long intValue0 = value0.asLong();
                final Long intValue1 = value1.asLong();

                final Long newIntValue = (intValue1 * intValue0);
                if (_didIntegerOverflow(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case DIVIDE: {
                // value0 value1 DIVIDE -> { value0 / value1 }
                // { 0x0A } { 0x02 } DIVIDE -> { 0x05 }

                final Value value1 = stack.pop(); // Divisor
                final Value value0 = stack.pop();

                final Long intValue0 = value0.asLong();
                final Long intValue1 = value1.asLong();

                if (intValue1 == 0) { return false; }

                final Long newIntValue = (intValue0 / intValue1);
                if (_didIntegerOverflow(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case MODULUS: {
                // value0 value1 MODULUS -> { value0 % value1 }
                // { 0x0A } { 0x02 } MODULUS -> { 0x00 }

                final Value value1 = stack.pop();
                final Value value0 = stack.pop();

                final Long intValue0 = value0.asLong();
                final Long intValue1 = value1.asLong();

                if (intValue1 == 0) { return false; }

                final Long newIntValue = (intValue0 % intValue1);
                if (_didIntegerOverflow(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case MIN: {
                final Value value1 = stack.pop();
                final Value value0 = stack.pop();

                final Long intValue0 = value0.asLong();
                final Long intValue1 = value1.asLong();

                final Long newIntValue = Math.min(intValue1, intValue0);
                if (_didIntegerOverflow(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            case MAX: {
                final Value value1 = stack.pop();
                final Value value0 = stack.pop();

                final Long intValue0 = value0.asLong();
                final Long intValue1 = value1.asLong();

                final Long newIntValue = Math.max(intValue1, intValue0);
                if (_didIntegerOverflow(newIntValue)) { return false; }

                final Value newValue = Value.fromInteger(newIntValue);
                stack.push(newValue);

                return (! stack.didOverflow());
            }

            default: { return false; }
        }
    }
}
