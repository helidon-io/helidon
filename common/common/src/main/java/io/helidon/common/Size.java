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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A definition of size in bytes.
 */
public interface Size {
    /**
     * Empty size - zero bytes.
     */
    Size ZERO = Size.create(0);

    /**
     * Create a new size with explicit number of bytes.
     *
     * @param size number of bytes
     * @return a new size instance
     */
    static Size create(long size) {
        return new SizeImpl(BigInteger.valueOf(size));
    }

    /**
     * Create a new size from amount and unit.
     *
     * @param amount amount in the provided unit
     * @param unit   unit
     * @return size representing the amount
     */
    static Size create(long amount, Unit unit) {
        Objects.requireNonNull(unit, "Unit must not be null");

        return new SizeImpl(BigInteger.valueOf(amount).multiply(unit.bytesInteger()));
    }

    /**
     * Create a new size from amount and unit.
     *
     * @param amount amount that can be decimal
     * @param unit   unit
     * @return size representing the amount
     * @throws IllegalArgumentException in case the amount cannot be converted to whole bytes (i.e. it has
     *                                  a fraction of byte)
     */
    static Size create(BigDecimal amount, Unit unit) {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(unit, "Unit must not be null");

        BigDecimal result = amount.multiply(new BigDecimal(unit.bytesInteger()));
        return new SizeImpl(result.toBigIntegerExact());
    }

    /**
     * Crete a new size from the size string.
     * The string may contain a unit. If a unit is not present, the size string is considered to be number of bytes.
     * <p>
     * We understand units from kilo (meaning 1000 or 1024, see table below), to exa bytes.
     * Each higher unit is either 1000 times or 1024 times bigger than the one below, depending on the approach used.
     * <p>
     * Measuring approaches and their string representations:
     * <ul>
     *     <li>KB, KiB - kibi, kilobinary, stands for 1024 bytes</li>
     *     <li>kB, kb - kilobyte, stands for 1000 bytes</li>
     *     <li>MB, MiB - mebi, megabinary, stands for 1024*1024 bytes</li>
     *     <li>mB, mb - megabyte, stands for 1000*1000 bytes</li>
     *     <li>From here the same concept is applied with Giga, Tera, Peta, and Exa bytes</li>
     * </ul>
     *
     * @param sizeString the string definition, such as {@code 76 MB}, or {@code 976 mB}, can also be a decimal number
     *                   - we use {@link java.math.BigDecimal} to parse the numeric section of the size; if there is a unit
     *                   defined, it must be separated by a single space from the numeric section
     * @return parsed size that can provide exact number of bytes
     */
    static Size parse(String sizeString) {
        Objects.requireNonNull(sizeString, "Size string is null");

        String parsed = sizeString.trim();
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("Size string is empty.");
        }
        int lastSpace = parsed.lastIndexOf(' ');
        if (lastSpace == -1) {
            // no unit
            return create(new BigDecimal(parsed), Unit.BYTE);
        }
        String size = parsed.substring(0, lastSpace);
        Unit unit = Unit.parse(parsed.substring(lastSpace + 1));
        BigDecimal amount = new BigDecimal(size);
        return create(amount, unit);
    }

    /**
     * Amount of units in this size.
     *
     * @param unit to get the size of
     * @return size in the provided unit as a big decimal
     * @throws ArithmeticException in case this size cannot be converted to the specified unit without losing
     *                             information
     * @see #toBytes()
     */
    BigDecimal to(Unit unit);

    /**
     * Number of bytes this size represents.
     *
     * @return number of bytes
     * @throws ArithmeticException in case the amount is higher than {@link Long#MAX_VALUE}, or would contain
     *                             fractions of byte
     */
    long toBytes();

    /**
     * Get the highest possible unit of the size with integer amount.
     *
     * @param unitKind kind of unit to print (kB, kb, KB, or KiB)
     * @return amount integer with a unit, such as {@code 270 kB}, if the amount is {@code 2000 kB}, this method would return
     *         {@code 2 mB} instead for {@link io.helidon.common.Size.UnitKind#DECIMAL_UPPER_CASE}
     */
    String toString(UnitKind unitKind);

    /**
     * Get the amount in the provided unit as a decimal number if needed. If the amount cannot be correctly
     * expressed in the provided unit, an exception is thrown.
     *
     * @param unit     unit to use, such as {@link io.helidon.common.Size.Unit#MIB}
     * @param unitKind kind of unit for the output, must match the provided unit,
     *                 such as {@link io.helidon.common.Size.UnitKind#BINARY_BI} to print {@code MiB}
     * @return amount decimal with a unit, such as {@code 270.426 MiB}
     * @throws java.lang.IllegalArgumentException in case the unitKind does not match the unit
     */
    String toString(Unit unit, UnitKind unitKind);

    /**
     * Kind of units, used for printing out the correct unit.
     */
    enum UnitKind {
        /**
         * The first letter (if two lettered) is lower case, the second is upper case, such ase
         * {@code B, kB, mB}. These represent powers of 1000.
         */
        DECIMAL_UPPER_CASE(false),
        /**
         * All letters are lower case, such as
         * {@code b, kb, mb}. These represent powers of 1000.
         */
        DECIMAL_LOWER_CASE(false),
        /**
         * The multiplier always contains {@code i}, the second is upper case B, such ase
         * {@code B, KiB, MiB}. These represent powers of 1024.
         */
        BINARY_BI(true),
        /**
         * All letters are upper case, such as
         * {@code B, KB, MB}. These represent powers of 1024.
         */
        BINARY_UPPER_CASE(true);
        private final boolean isBinary;

        UnitKind(boolean isBinary) {
            this.isBinary = isBinary;
        }

        boolean isBinary() {
            return isBinary;
        }
    }

    /**
     * Units that can be used.
     */
    enum Unit {
        /**
         * Bytes.
         */
        BYTE(1024, 0, "b", "B"),
        /**
         * Kilobytes (represented as {@code kB}), where {@code kilo} is used in its original meaning as a thousand,
         * i.e. 1 kB is 1000 bytes.
         */
        KB(1000, 1, "kB", "kb"),
        /**
         * Kibi-bytes (represented as either {@code KB} or {@code KiB}), where we use binary approach, i.e.
         * 1 KB or KiB is 1024 bytes.
         */
        KIB(1024, 1, "KB", "KiB"),
        /**
         * Megabytes (represented as {@code mB}), where {@code mega} is used in its original meaning as a million,
         * i.e. 1 mB is 1000^2 bytes (1000 to the power of 2), or 1000 kB.
         */
        MB(1000, 2, "mB", "mb"),
        /**
         * Mebi-bytes (represented as either {@code MB} or {@code MiB}), where we use binary approach, i.e.
         * 1 MB or MiB is 1024^2 bytes (1024 to the power 2), or 1024 KiB.
         */
        MIB(1024, 2, "MB", "MiB"),
        /**
         * Gigabytes (represented as {@code gB}):
         * i.e. 1 gB is 1000^3 bytes (1000 to the power of 3), or 1000 mB.
         */
        GB(1000, 3, "gB", "gb"),
        /**
         * Gibi-bytes (represented as either {@code GB} or {@code GiB}), where we use binary approach, i.e.
         * 1 GB or GiB is 1024^3 bytes (1024 to the power 3), or 1024 MiB.
         */
        GIB(1024, 3, "GB", "GiB"),
        /**
         * Terabytes (represented as {@code tB}):
         * i.e. 1 gB is 1000^4 bytes (1000 to the power of 4), or 1000 gB.
         */
        TB(1000, 4, "tB", "tb"),
        /**
         * Tebi-bytes (represented as either {@code TB} or {@code TiB}), where we use binary approach, i.e.
         * 1 TB or TiB is 1024^4 bytes (1024 to the power 4), or 1024 GiB.
         */
        TIB(1024, 4, "TB", "TiB"),
        /**
         * Petabytes (represented as {@code pB}):
         * i.e. 1 pB is 1000^5 bytes (1000 to the power of 5), or 1000 tB.
         */
        PB(1000, 5, "pB", "pb"),
        /**
         * Pebi-bytes (represented as either {@code PB} or {@code PiB}), where we use binary approach, i.e.
         * 1 PB or PiB is 1024^5 bytes (1024 to the power 5), or 1024 TiB.
         */
        PIB(1024, 5, "PB", "PiB"),
        /**
         * Exabytes (represented as {@code eB}):
         * i.e. 1 eB is 1000^6 bytes (1000 to the power of 6), or 1000 pB.
         */
        EB(1000, 6, "eB", "eb"),
        /**
         * Exbi-bytes (represented as either {@code EB} or {@code EiB}), where we use binary approach, i.e.
         * 1 EB or EiB is 1024^6 bytes (1024 to the power 6), or 1024 PiB.
         */
        EIB(1024, 6, "EB", "EiB");

        private static final Map<String, Unit> UNIT_MAP;

        static {
            Map<String, Unit> units = new HashMap<>();
            for (Unit unit : Unit.values()) {
                for (String validUnitString : unit.units) {
                    units.put(validUnitString, unit);
                }
            }
            UNIT_MAP = Map.copyOf(units);
        }

        private final long bytes;
        private final int power;
        private final BigInteger bytesInteger;
        private final Set<String> units;
        private final boolean binary;
        private final String firstUnit;
        private final String secondUnit;

        /**
         * Unit.
         *
         * @param base       base of the calculation (1000 or 1024)
         * @param power      to the power of
         * @param firstUnit  first unit (either upper case decimal [mB], or all upper case [MB])
         * @param secondUnit second unit (either lower case decimal [mb], or binary unit name [MiB])
         */
        Unit(int base, int power, String firstUnit, String secondUnit) {
            this.firstUnit = firstUnit;
            this.secondUnit = secondUnit;
            this.units = Set.of(firstUnit, secondUnit);
            this.bytes = (long) Math.pow(base, power);
            this.bytesInteger = BigInteger.valueOf(bytes);
            this.power = power;
            this.binary = base == 1024;
        }

        /**
         * Parse the size string to appropriate unit.
         *
         * @param unitString defines the unit, such as {@code KB}, {@code MiB}, {@code pB} etc.; empty string parses to
         *                   {@link #BYTE}
         * @return a parsed unit
         * @throws IllegalArgumentException if the unit cannot be parsed
         */
        public static Unit parse(String unitString) {
            if (unitString.isEmpty()) {
                return BYTE;
            }
            Unit unit = UNIT_MAP.get(unitString);
            if (unit == null) {
                throw new IllegalArgumentException("Unknown unit: " + unitString);
            }
            return unit;
        }

        /**
         * Number of bytes this unit represents.
         *
         * @return number of bytes of this unit
         */
        public long bytes() {
            return bytes;
        }

        /**
         * Number of bytes in this unit (exact integer).
         *
         * @return number of bytes this unit contains
         */
        public BigInteger bytesInteger() {
            return bytesInteger;
        }

        String unitString(UnitKind unitKind) {
            if (power == 0) {
                if (unitKind == UnitKind.DECIMAL_LOWER_CASE) {
                    return "b";
                }
                return "B";
            }

            return switch (unitKind) {
                case DECIMAL_UPPER_CASE, BINARY_UPPER_CASE -> firstUnit;
                case DECIMAL_LOWER_CASE, BINARY_BI -> secondUnit;
            };
        }

        boolean isBinary() {
            return binary;
        }
    }
}
