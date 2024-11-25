/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.common;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

class SizeImpl implements Size {
    private final BigInteger bytes;

    SizeImpl(BigInteger bytes) {
        this.bytes = bytes;
    }

    @Override
    public BigDecimal to(Unit unit) {
        Objects.requireNonNull(unit, "Unit must not be null");

        BigDecimal bigDecimal = new BigDecimal(unit.bytesInteger());
        BigDecimal result = new BigDecimal(bytes).divide(bigDecimal,
                                                         bigDecimal.precision() + 1,
                                                         RoundingMode.UNNECESSARY);
        return result.stripTrailingZeros();
    }

    @Override
    public long toBytes() {
        try {
            return bytes.longValueExact();
        } catch (ArithmeticException e) {
            // we cannot use a cause with constructor, creating a more descriptive message
            throw new ArithmeticException("Size " + this + " cannot be converted to number of bytes, out of long range.");
        }
    }

    @Override
    public String toString(UnitKind unitKind) {
        Objects.requireNonNull(unitKind, "Unit kind must not be null");

        if (bytes.equals(BigInteger.ZERO)) {
            return "0 " + Unit.BYTE.unitString(unitKind);
        }

        // try each amount from the highest that returns zero decimal places
        Unit[] values = Unit.values();
        for (int i = values.length - 1; i >= 0; i--) {
            Unit value = values[i];
            if (value.isBinary() != unitKind.isBinary()) {
                continue;
            }
            BigDecimal bigDecimal = to(value);
            try {
                // try to convert without any decimal spaces
                BigInteger bi = bigDecimal.toBigIntegerExact();
                return bi + " " + value.unitString(unitKind);
            } catch (Exception ignored) {
                // ignored, we cannot convert to this unit, because it cannot be correctly divided
            }
        }

        return bytes + " " + Unit.BYTE.unitString(unitKind);
    }

    @Override
    public String toString(Unit unit, UnitKind unitKind) {
        Objects.requireNonNull(unit, "Unit must not be null");
        Objects.requireNonNull(unitKind, "Unit kind must not be null");

        if (unit.isBinary() != unitKind.isBinary()) {
            throw new IllegalArgumentException("Unit " + unit + " does not match kind " + unitKind);
        }
        String unitString = unit.unitString(unitKind);
        BigDecimal amount = to(unit);

        return amount.toPlainString() + " " + unitString;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Size size)) {
            return false;
        }
        return Objects.equals(size.to(Unit.BYTE), this.to(Unit.BYTE));
    }

    @Override
    public int hashCode() {
        return Objects.hash(to(Unit.BYTE));
    }

    @Override
    public String toString() {
        return toString(UnitKind.DECIMAL_UPPER_CASE);
    }
}
