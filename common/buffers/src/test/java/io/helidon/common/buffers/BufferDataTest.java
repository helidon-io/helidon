/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.common.buffers;

import java.io.InputStream;
import java.util.HexFormat;
import java.util.stream.Stream;

import org.hamcrest.Matchers;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class BufferDataTest {
    static Stream<TestContext> initParams() {
        return Stream.of(new TestContext("fixed", BufferData.create(1024)),
                         new TestContext("growing", BufferData.growing(0)),
                         new TestContext("byte[]", BufferData.create(new byte[1024]).clear()));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    void indexOf(TestContext context) {
        BufferData b = context.bufferData();
        b.writeAscii("Hello: test: not: test: end");

        int index = b.indexOf(Bytes.COLON_BYTE);

        assertThat(index, is(5));

        String nextString = b.readString(index);
        assertThat(nextString, is("Hello"));
        b.skip(2); // skip the delimiter and space
        index = b.indexOf(Bytes.COLON_BYTE);

        assertThat(index, is(4));
        nextString = b.readString(index);
        assertThat(nextString, is("test"));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    void lastIndexOf(TestContext context) {
        BufferData b = context.bufferData();
        b.writeAscii("Hello: test: not: test: end");
        int index = b.lastIndexOf((byte) ':');

        assertThat(index, is(22));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    void testWriteHpackInt(TestContext context) {
        int value = 10;

        BufferData bufferData = context.bufferData()
                .writeHpackInt(value, 0b10100000, 5);

        assertThat(bufferData.available(), is(1));
        assertThat(bufferData.read(), is((0b10100000 | value)));

        value = 1337;
        byte[] expected = new byte[] {(byte) 0b10111111,
                (byte) 0b10011010,
                (byte) 0b00001010};

        bufferData.clear();
        bufferData.writeHpackInt(value, 0b10100000, 5);

        assertThat(bufferData.available(), is(expected.length));
        byte[] actual = new byte[expected.length];
        int read = bufferData.read(actual);
        assertThat(read, is(expected.length));

        for (int i = 0; i < actual.length; i++) {
            byte actualByte = actual[i];
            byte expectedByte = expected[i];

            assertThat("Byte at position " + i,
                       Integer.toBinaryString(expectedByte & 0xFF),
                       is(Integer.toBinaryString(actualByte & 0xFF)));
        }
    }

    @ParameterizedTest
    @MethodSource("initParams")
    void testReadInt(TestContext context) {
        // https://www.rfc-editor.org/rfc/rfc7541.html#appendix-C.1.1
        int expected = 10;
        BufferData buffer = context.bufferData();
        assertThat(buffer.readHpackInt(10, 5), is(expected));

        // https://www.rfc-editor.org/rfc/rfc7541.html#appendix-C.1.2
        expected = 1337;
        buffer.clear();
        buffer.write((byte) 0b10011010).write((byte) 0b00001010);
        assertThat(buffer.readHpackInt(31, 5), is(expected));

        // https://www.rfc-editor.org/rfc/rfc7541.html#appendix-C.1.3
        expected = 42;
        buffer.clear();
        assertThat(buffer.readHpackInt(42, 8), is(expected));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    void testBinaryOutput(TestContext context) {
        BufferData bd = context.bufferData();
        bd.write(0b01010101);
        String expected = """
                +--------+----------+
                |  index | 01234567 |
                +--------+----------+
                |00000000| 01010101 |
                +--------+----------+
                """;
        assertThat(bd.debugDataBinary(), is(expected));

        bd.clear();
        bd.write(0b00000000);
        bd.write(0b11111111);
        expected = """
                +--------+----------+
                |  index | 01234567 |
                +--------+----------+
                |00000000| 00000000 |
                |00000001| 11111111 |
                +--------+----------+
                """;
        assertThat(bd.debugDataBinary(), is(expected));

        bd.clear();
        bd.writeAscii("GET / HTTP/1.1\r\nhost: localhost:8080\r\n\r\n");

        expected = """
                +--------+----------+
                |  index | 01234567 |
                +--------+----------+
                |00000000| 01000111 |
                |00000001| 01000101 |
                |00000002| 01010100 |
                |00000003| 00100000 |
                |00000004| 00101111 |
                |00000005| 00100000 |
                |00000006| 01001000 |
                |00000007| 01010100 |
                |00000008| 01010100 |
                |00000009| 01010000 |
                |0000000a| 00101111 |
                |0000000b| 00110001 |
                |0000000c| 00101110 |
                |0000000d| 00110001 |
                |0000000e| 00001101 |
                |0000000f| 00001010 |
                |00000010| 01101000 |
                |00000011| 01101111 |
                |00000012| 01110011 |
                |00000013| 01110100 |
                |00000014| 00111010 |
                |00000015| 00100000 |
                |00000016| 01101100 |
                |00000017| 01101111 |
                |00000018| 01100011 |
                |00000019| 01100001 |
                |0000001a| 01101100 |
                |0000001b| 01101000 |
                |0000001c| 01101111 |
                |0000001d| 01110011 |
                |0000001e| 01110100 |
                |0000001f| 00111010 |
                |00000020| 00111000 |
                |00000021| 00110000 |
                |00000022| 00111000 |
                |00000023| 00110000 |
                |00000024| 00001101 |
                |00000025| 00001010 |
                |00000026| 00001101 |
                |00000027| 00001010 |
                +--------+----------+
                """;
        assertThat(bd.debugDataBinary(), is(expected));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    void testHexOutput16bytes(TestContext context) {
        BufferData bd = context.bufferData();
        bd.write(dataFromHex("82 86 84 41 8a 08 9d 5c 0b 81 70 dc 78 0f 03 ba"));
        String hex = bd.debugDataHex(true);
        String expected = """
                +--------+-------------------------------------------------+----------------+
                |   index|  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |            data|
                +--------+-------------------------------------------------+----------------+
                |00000000| 82 86 84 41 8a 08 9d 5c 0b 81 70 dc 78 0f 03 ba |...A...\\..pÜx..º|
                +--------+-------------------------------------------------+----------------+
                """;
        assertThat(hex, is(expected));
        bd.skip(8);
        hex = bd.debugDataHex(true);
        assertThat(hex, is(expected));

        hex = bd.debugDataHex(false);
        expected = """
                +--------+-------------------------------------------------+----------------+
                |   index|  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |            data|
                +--------+-------------------------------------------------+----------------+
                |00000000| 0b 81 70 dc 78 0f 03 ba                         |..pÜx..º        |
                +--------+-------------------------------------------------+----------------+
                """;
        assertThat(hex, is(expected));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    void testReadInt4bytes(TestContext context) {
        long maxValue = 0xFFFFFFFFL;
        BufferData bd = context.bufferData();
        bd.write(dataFromHex("FF FF FF FF"));
        long number = bd.readUnsignedInt32();
        assertThat(number, Matchers.greaterThan(0L));
        assertThat(number, is(maxValue));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    void testHexOutput(TestContext context) {
        BufferData bd = context.bufferData();
        bd.writeAscii("GET / HTTP/1.1\r\nhost: localhost:8080\r\n\r\n");
        String expected = """
                +--------+-------------------------------------------------+----------------+
                |   index|  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |            data|
                +--------+-------------------------------------------------+----------------+
                |00000000| 47 45 54 20 2f 20 48 54 54 50 2f 31 2e 31 0d 0a |GET / HTTP/1.1..|
                |00000010| 68 6f 73 74 3a 20 6c 6f 63 61 6c 68 6f 73 74 3a |host: localhost:|
                |00000020| 38 30 38 30 0d 0a 0d 0a                         |8080....        |
                +--------+-------------------------------------------------+----------------+
                """;
        assertThat(bd.debugDataHex(true), is(expected));

        bd.clear();
        bd.write(0b01010101);
        expected = """
                +--------+-------------------------------------------------+----------------+
                |   index|  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |            data|
                +--------+-------------------------------------------------+----------------+
                |00000000| 55                                              |U               |
                +--------+-------------------------------------------------+----------------+
                """;
        assertThat(bd.debugDataHex(true), is(expected));

        bd.clear();
        bd.write(0b00000000);
        bd.write(0b11111111);
        expected = """
                +--------+-------------------------------------------------+----------------+
                |   index|  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |            data|
                +--------+-------------------------------------------------+----------------+
                |00000000| 00 ff                                           |.ÿ              |
                +--------+-------------------------------------------------+----------------+
                """;
        assertThat(bd.debugDataHex(true), is(expected));

        bd.clear();
        expected = """
                +--------+-------------------------------------------------+----------------+
                |   index|  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |            data|
                +--------+-------------------------------------------------+----------------+
                +--------+-------------------------------------------------+----------------+
                """;
        assertThat(bd.debugDataHex(true), is(expected));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    void emptyInputStream(TestContext context) {
        BufferData b = context.bufferData();
        assertThat(b.available(), is(0));
        b.readFrom(new InputStream() {
            @Override
            public int read() {
                return -1;      // no data
            }
        });
        assertThat(b.available(), is(0));
    }

    BufferData dataFromHex(String hexEncoded) {
        byte[] bytes = HexFormat.of().parseHex(hexEncoded.replace(" ", ""));
        return BufferData.create(bytes);
    }

    private record TestContext(String name, BufferData bufferData) {
        @Override
        public String toString() {
            return name;
        }
    }
}