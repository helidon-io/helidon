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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SizeTest {
    @Test
    void testBytesEmpty() {
        Size first = Size.create(0);
        Size second = Size.ZERO;

        assertThat(first, is(second));
        assertThat(first.hashCode(), is(second.hashCode()));

        assertThat(first.toBytes(), is(0L));
        assertThat(second.toBytes(), is(0L));

        for (Size.Unit unit : Size.Unit.values()) {
            assertThat(first.to(unit), is(BigDecimal.ZERO));
            assertThat(second.to(unit), is(BigDecimal.ZERO));
        }

        assertThat(first.toString(), is("0 B"));
        assertThat(first.toString(Size.UnitKind.DECIMAL_LOWER_CASE), is("0 b"));
        assertThat(first.toString(Size.Unit.EIB, Size.UnitKind.BINARY_BI), is("0 EiB"));
    }

    @Test
    void testTooBig() {
        Size size = Size.create(Long.MAX_VALUE, Size.Unit.KB);
        assertThrows(ArithmeticException.class, size::toBytes);
    }

    @Test
    void testToStringWrongUnitKind() {
        Size size = Size.create(1024, Size.Unit.KIB);
        assertThrows(IllegalArgumentException.class, () -> size.toString(Size.Unit.EB, Size.UnitKind.BINARY_BI));
        assertThrows(IllegalArgumentException.class, () -> size.toString(Size.Unit.EB, Size.UnitKind.BINARY_UPPER_CASE));
        assertThrows(IllegalArgumentException.class, () -> size.toString(Size.Unit.EIB, Size.UnitKind.DECIMAL_UPPER_CASE));
        assertThrows(IllegalArgumentException.class, () -> size.toString(Size.Unit.EIB, Size.UnitKind.DECIMAL_LOWER_CASE));
    }

    @Test
    void testConversionsBinary() {
        Size size = Size.create(1, Size.Unit.EIB);

        assertThat(size.toBytes(), is(1152921504606846976L));

        assertThat(size.to(Size.Unit.BYTE), is(new BigDecimal(Size.Unit.EIB.bytesInteger())));
        assertThat(size.to(Size.Unit.KIB), is(BigDecimal.valueOf(1125899906842624L)));
        assertThat(size.to(Size.Unit.MIB), is(BigDecimal.valueOf(1099511627776L)));
        assertThat(size.to(Size.Unit.GIB), is(BigDecimal.valueOf(1073741824L)));
        assertThat(size.to(Size.Unit.TIB), is(BigDecimal.valueOf(1048576L)));
        assertThat(size.to(Size.Unit.PIB), is(BigDecimal.valueOf(1024L)));
        assertThat(size.to(Size.Unit.EIB), is(BigDecimal.valueOf(1L)));

        assertThat(size.toString(Size.UnitKind.BINARY_UPPER_CASE), is("1 EB"));
        assertThat(size.toString(Size.UnitKind.BINARY_BI), is("1 EiB"));
        assertThat(size.toString(Size.Unit.GIB, Size.UnitKind.BINARY_BI), is("1073741824 GiB"));
        assertThat(size.toString(Size.Unit.GIB, Size.UnitKind.BINARY_UPPER_CASE), is("1073741824 GB"));
    }

    @Test
    void testConversionsDecimal() {
        Size size = Size.create(1048576, Size.Unit.BYTE);

        assertThat(size.toBytes(), is(1048576L));

        assertThat(size.to(Size.Unit.BYTE), is(BigDecimal.valueOf(1048576L)));
        assertThat(size.to(Size.Unit.KB), is(new BigDecimal("1048.576")));
        assertThat(size.to(Size.Unit.MB), is(new BigDecimal("1.048576")));
        assertThat(size.to(Size.Unit.GB), closeTo(new BigDecimal("0.001048576"), BigDecimal.ZERO));
        assertThat(size.to(Size.Unit.TB), closeTo(new BigDecimal("0.000001048576"), BigDecimal.ZERO));
        assertThat(size.to(Size.Unit.PB), closeTo(new BigDecimal("0.000000001048576"), BigDecimal.ZERO));
        assertThat(size.to(Size.Unit.EB), closeTo(new BigDecimal("0.000000000001048576"), BigDecimal.ZERO));

        assertThat(size.toString(), is("1048576 B"));
        assertThat(size.toString(Size.UnitKind.DECIMAL_LOWER_CASE), is("1048576 b"));
        assertThat(size.toString(Size.Unit.EB, Size.UnitKind.DECIMAL_UPPER_CASE), is("0.000000000001048576 eB"));
    }

    @Test
    void testParsingDecimal() {
        testParsing("10", 10);
        testParsing("2 kb", 2_000);
        testParsing("2 kB", 2_000);
        testParsing("3 mB", 3_000_000);
        testParsing("3 mb", 3_000_000);
        testParsing("4 gB", 4_000_000_000L);
        testParsing("4 gb", 4_000_000_000L);
        testParsing("7 tB", 7_000_000_000_000L);
        testParsing("7 tb", 7_000_000_000_000L);
        testParsing("5 pB", 5_000_000_000_000_000L);
        testParsing("5 pb", 5_000_000_000_000_000L);
        testParsing("6 eB", 6_000_000_000_000_000_000L);
        testParsing("6 eb", 6_000_000_000_000_000_000L);

        testParsing("2.42 kb", 2_420);
        testParsing("2.42 kB", 2_420);
        testParsing("3.42 mB", 3_420_000);
        testParsing("3.42 mb", 3_420_000);
        testParsing("4.42 gB", 4_420_000_000L);
        testParsing("4.42 gb", 4_420_000_000L);
        testParsing("7.42 tB", 7_420_000_000_000L);
        testParsing("7.42 tb", 7_420_000_000_000L);
        testParsing("5.42 pB", 5_420_000_000_000_000L);
        testParsing("5.42 pb", 5_420_000_000_000_000L);
        testParsing("6.42 eB", 6_420_000_000_000_000_000L);
        testParsing("6.42 eb", 6_420_000_000_000_000_000L);
    }

    @Test
    void testParsingBinary() {
        testParsing("10", 10);
        testParsing("2 KB", 2_048);
        testParsing("2 KiB", 2_048);
        testParsing("3 MB", 3_145_728);
        testParsing("3 MiB", 3_145_728);
        testParsing("4 GB", 4_294_967_296L);
        testParsing("4 GiB", 4_294_967_296L);
        testParsing("7 TB", 7_696_581_394_432L);
        testParsing("7 TiB", 7_696_581_394_432L);
        testParsing("5 PB", 5_629_499_534_213_120L);
        testParsing("5 PiB", 5_629_499_534_213_120L);
        testParsing("6 EB", 6_917_529_027_641_081_856L);
        testParsing("6 EiB", 6_917_529_027_641_081_856L);

        testParsing("3.5 KB", 3_584);
        testParsing("3.5 KiB", 3_584);
        // not testing others, as this combines decimal numbers with binary numbers
    }

    private void testParsing(String value, long numberOfBytes) {
        Size size = Size.parse(value);
        assertThat(size.toBytes(), is(numberOfBytes));
    }
}

