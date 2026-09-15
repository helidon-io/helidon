/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.http.http3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.HuffmanCodec;
import io.helidon.common.buffers.PrefixedIntegerCodec;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;

/**
 * Shared QPACK field-section encoding and decoding utilities.
 */
final class QpackCodec {
    private QpackCodec() {
    }

    /**
     * Encode the provided headers as a QPACK field section.
     *
     * @param headers headers to encode
     * @return encoded field section
     */
    static byte[] encodeHeaders(Iterable<Header> headers) {
        BufferData output = BufferData.growing(128);
        writeFieldSectionPrefix(output, 0, 0, 0);
        for (Header header : headers) {
            for (String value : header.allValues()) {
                writeLiteralFieldLine(output, header.headerName().lowerCase(), value);
            }
        }
        return output.readBytes();
    }

    /**
     * Decode a QPACK field section that does not use dynamic-table references.
     *
     * @param buffer encoded field section
     * @return decoded headers
     */
    static Headers decodeHeaders(BufferData buffer) {
        return decodeHeaders(buffer, -1);
    }

    /**
     * Decode a QPACK field section that does not use dynamic-table references.
     *
     * @param buffer encoded field section
     * @param maxFieldSectionSize maximum decoded field section size in octets, or a negative value to disable the limit
     * @return decoded headers
     */
    static Headers decodeHeaders(BufferData buffer, long maxFieldSectionSize) {
        WritableHeaders<?> headers = WritableHeaders.create();
        decodeHeaderLines(buffer, maxFieldSectionSize).forEach(headers::add);
        return headers;
    }

    /**
     * Decode a QPACK field section into ordered header lines that preserve the wire sequence.
     *
     * @param buffer encoded field section
     * @return decoded header lines in wire order
     */
    static List<Header> decodeHeaderLines(BufferData buffer) {
        return decodeHeaderLines(buffer, -1);
    }

    /**
     * Decode a QPACK field section into ordered header lines that preserve the wire sequence.
     *
     * @param buffer encoded field section
     * @param maxFieldSectionSize maximum decoded field section size in octets, or a negative value to disable the limit
     * @return decoded header lines in wire order
     */
    static List<Header> decodeHeaderLines(BufferData buffer, long maxFieldSectionSize) {
        FieldSectionPrefix prefix = readFieldSectionPrefix(buffer, 0, 0);
        if (prefix.requiredInsertCount() != 0 || prefix.base() != 0) {
            throw new IllegalArgumentException("Dynamic QPACK table references are not supported");
        }

        FieldSectionSizeTracker sizeTracker = fieldSectionSizeTracker(maxFieldSectionSize);
        List<Header> headers = new ArrayList<>();
        while (buffer.available() > 0) {
            int first = buffer.get(0) & 0xff;
            if ((first & 0x80) != 0) {
                headers.add(indexedFieldLine(buffer, sizeTracker));
            } else if ((first & 0x40) != 0) {
                headers.add(literalWithNameReference(buffer, sizeTracker));
            } else if ((first & 0x20) != 0) {
                headers.add(literalWithLiteralName(buffer, sizeTracker));
            } else {
                throw new IllegalArgumentException("Unsupported QPACK field line: 0x" + Integer.toHexString(first));
            }
        }
        return headers;
    }

    static FieldSectionPrefix readFieldSectionPrefix(BufferData buffer,
                                                     long insertCount,
                                                     long maxEntries) {
        long encodedRequiredInsertCount = readPrefixedInteger(buffer, 8);
        if (buffer.available() == 0) {
            throw new IllegalArgumentException("Malformed QPACK field section prefix");
        }
        int first = buffer.get(0) & 0xff;
        int signBit = (first & 0x80) == 0 ? 0 : 1;
        long deltaBase = readPrefixedInteger(buffer, 7);

        if (encodedRequiredInsertCount == 0) {
            if (signBit == 1) {
                throw new IllegalArgumentException("QPACK field section Base must not be negative");
            }
            return new FieldSectionPrefix(0, 0);
        }

        long requiredInsertCount = requiredInsertCount(insertCount, maxEntries, encodedRequiredInsertCount);

        if (signBit == 1 && requiredInsertCount <= deltaBase) {
            throw new IllegalArgumentException("QPACK field section Base must not be negative");
        }
        if (signBit == 0 && deltaBase > Long.MAX_VALUE - requiredInsertCount) {
            throw new IllegalArgumentException("QPACK field section Base exceeds the supported range");
        }
        long base = signBit == 0 ? requiredInsertCount + deltaBase : requiredInsertCount - deltaBase - 1;
        return new FieldSectionPrefix(requiredInsertCount, base);
    }

    static void writeFieldSectionPrefix(BufferData output,
                                        long requiredInsertCount,
                                        long base,
                                        long maxEntries) {
        if (requiredInsertCount == 0) {
            writePrefixedInteger(output, 8, 0, 0);
            writePrefixedInteger(output, 7, 0, 0);
            return;
        }
        if (maxEntries == 0) {
            throw new IllegalArgumentException("Dynamic QPACK table is disabled");
        }

        long encodedRequiredInsertCount = (requiredInsertCount % (2 * maxEntries)) + 1;
        int signBit = base >= requiredInsertCount ? 0 : 0x80;
        long deltaBase = base >= requiredInsertCount
                ? base - requiredInsertCount
                : requiredInsertCount - base - 1;

        writePrefixedInteger(output, 8, 0, encodedRequiredInsertCount);
        writePrefixedInteger(output, 7, signBit, deltaBase);
    }

    static void writeIndexedFieldLine(BufferData output,
                                      long absoluteIndex,
                                      boolean fromStaticTable,
                                      long base) {
        if (fromStaticTable) {
            writePrefixedInteger(output, 6, 0b1100_0000, absoluteIndex);
            return;
        }
        writePrefixedInteger(output, 6, 0b1000_0000, base - 1 - absoluteIndex);
    }

    static void writeLiteralWithNameReference(BufferData output,
                                              long absoluteIndex,
                                              boolean fromStaticTable,
                                              long base,
                                              String value) {
        if (fromStaticTable) {
            writePrefixedInteger(output, 4, 0b0101_0000, absoluteIndex);
        } else {
            writePrefixedInteger(output, 4, 0b0100_0000, base - 1 - absoluteIndex);
        }
        writeString(output, 7, 0, value);
    }

    static void writeLiteralFieldLine(BufferData output, String name, String value) {
        writeString(output, 3, 0b0010_0000, name);
        writeString(output, 7, 0, value);
    }

    static String readString(BufferData buffer,
                             int prefixBits,
                             FieldSectionSizeTracker sizeTracker) {
        if (buffer.available() == 0) {
            throw new IllegalArgumentException("Malformed QPACK string");
        }
        int first = buffer.get(0) & 0xff;
        int huffmanBit = 1 << prefixBits;
        boolean huffman = (first & huffmanBit) != 0;
        long length = readPrefixedInteger(buffer, prefixBits);
        if (length > buffer.available()) {
            throw new IllegalArgumentException("Malformed QPACK string");
        }
        byte[] bytes = new byte[Math.toIntExact(length)];
        buffer.read(bytes);
        return decodeStringBytes(BufferData.create(bytes), huffman, sizeTracker);
    }

    static String decodeStringBytes(byte[] bytes, boolean huffman) {
        return decodeStringBytes(BufferData.create(bytes), huffman, FieldSectionSizeTracker.UNBOUNDED);
    }

    static String decodeStringBytes(BufferData bytes, boolean huffman) {
        return decodeStringBytes(bytes, huffman, FieldSectionSizeTracker.UNBOUNDED);
    }

    static String decodeStringBytes(BufferData bytes,
                                    boolean huffman,
                                    FieldSectionSizeTracker sizeTracker) {
        int encodedLength = bytes.available();
        if (!huffman) {
            sizeTracker.consume(encodedLength);
            return bytes.readString(encodedLength, StandardCharsets.ISO_8859_1);
        }
        int decodedCapacity = huffmanDecodedCapacity(encodedLength);
        StringBuilder builder = new StringBuilder(sizeTracker.initialStringCapacity(decodedCapacity));
        Appendable destination = sizeTracker.wrap(builder);
        HuffmanCodec.decode(bytes, encodedLength, destination);
        return builder.toString();
    }

    static void writeString(BufferData output,
                            int prefixBits,
                            int leadingBits,
                            String value) {
        byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        writePrefixedInteger(output, prefixBits, leadingBits, bytes.length);
        output.write(bytes);
    }

    static long readPrefixedInteger(BufferData buffer, int prefixBits) {
        if (buffer.available() == 0) {
            throw new IllegalArgumentException("Malformed QPACK integer");
        }
        int first = buffer.read() & 0xff;
        try {
            return PrefixedIntegerCodec.readLong(buffer, first, prefixBits);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Malformed QPACK integer", e);
        }
    }

    static void writePrefixedInteger(BufferData output,
                                     int prefixBits,
                                     int leadingBits,
                                     long value) {
        PrefixedIntegerCodec.writeLong(output, value, leadingBits, prefixBits);
    }

    static FieldSectionSizeTracker fieldSectionSizeTracker(long maxFieldSectionSize) {
        return maxFieldSectionSize < 0 ? FieldSectionSizeTracker.UNBOUNDED : new FieldSectionSizeTracker(maxFieldSectionSize);
    }

    private static long requiredInsertCount(long insertCount, long maxEntries, long encodedRequiredInsertCount) {
        if (maxEntries == 0) {
            throw new IllegalArgumentException("Dynamic QPACK table references are not supported");
        }

        long fullRange = 2 * maxEntries;
        if (encodedRequiredInsertCount > fullRange) {
            throw new IllegalArgumentException("Malformed QPACK required insert count");
        }

        long maxValue = insertCount + maxEntries;
        long maxWrapped = (maxValue / fullRange) * fullRange;
        long requiredInsertCount = maxWrapped + encodedRequiredInsertCount - 1;
        if (requiredInsertCount > maxValue) {
            if (requiredInsertCount <= fullRange) {
                throw new IllegalArgumentException("Malformed QPACK required insert count");
            }
            requiredInsertCount -= fullRange;
        }
        if (requiredInsertCount == 0) {
            throw new IllegalArgumentException("Malformed QPACK required insert count");
        }
        return requiredInsertCount;
    }

    private static int huffmanDecodedCapacity(int encodedLength) {
        return (int) Math.min(Integer.MAX_VALUE, (encodedLength * 8L + 4) / 5);
    }

    private static Header indexedFieldLine(BufferData buffer,
                                           FieldSectionSizeTracker sizeTracker) {
        int first = buffer.get(0) & 0xff;
        boolean fromStatic = (first & 0x40) != 0;
        long index = readPrefixedInteger(buffer, 6);
        if (!fromStatic) {
            throw new IllegalArgumentException("Dynamic QPACK table references are not supported");
        }
        HeaderField field = QpackStaticTable.get(index);
        sizeTracker.beginFieldLine();
        sizeTracker.consume(field.name().length());
        sizeTracker.consume(field.value().length());
        return HeaderValues.create(HeaderNames.createFromLowercase(field.name()), field.value());
    }

    private static Header literalWithNameReference(BufferData buffer,
                                                   FieldSectionSizeTracker sizeTracker) {
        int first = buffer.get(0) & 0xff;
        boolean fromStatic = (first & 0x10) != 0;
        long index = readPrefixedInteger(buffer, 4);
        if (!fromStatic) {
            throw new IllegalArgumentException("Dynamic QPACK table references are not supported");
        }
        String name = QpackStaticTable.get(index).name();
        sizeTracker.beginFieldLine();
        sizeTracker.consume(name.length());
        String value = readString(buffer, 7, sizeTracker);
        return HeaderValues.create(HeaderNames.createFromLowercase(name), value);
    }

    private static Header literalWithLiteralName(BufferData buffer,
                                                 FieldSectionSizeTracker sizeTracker) {
        sizeTracker.beginFieldLine();
        String name = readString(buffer, 3, sizeTracker);
        String value = readString(buffer, 7, sizeTracker);
        return HeaderValues.create(HeaderNames.createFromLowercase(name), value);
    }

    record FieldSectionPrefix(long requiredInsertCount, long base) {
    }

    static final class FieldSectionSizeTracker {
        private static final long FIELD_LINE_OVERHEAD = 32L;
        private static final FieldSectionSizeTracker UNBOUNDED = new FieldSectionSizeTracker(-1);

        private final long maxFieldSectionSize;
        private long currentSize;

        private FieldSectionSizeTracker(long maxFieldSectionSize) {
            this.maxFieldSectionSize = maxFieldSectionSize;
        }

        int initialStringCapacity(int decodedCapacity) {
            if (maxFieldSectionSize < 0) {
                return decodedCapacity;
            }
            return (int) Math.min(decodedCapacity, maxFieldSectionSize - currentSize);
        }

        void beginFieldLine() {
            consume(FIELD_LINE_OVERHEAD);
        }

        void consume(long octets) {
            if (maxFieldSectionSize < 0 || octets <= 0) {
                return;
            }
            if (currentSize > maxFieldSectionSize - octets) {
                throw Http3ProtocolException.streamError(Http3ErrorCode.MESSAGE_ERROR,
                                                         "HTTP/3 field section exceeds configured maximum size: "
                                                                 + (currentSize + octets)
                                                                 + " > "
                                                                 + maxFieldSectionSize);
            }
            currentSize += octets;
        }

        Appendable wrap(Appendable delegate) {
            if (maxFieldSectionSize < 0) {
                return delegate;
            }
            return new CountingAppendable(this, delegate);
        }
    }

    private static final class CountingAppendable implements Appendable {
        private final FieldSectionSizeTracker sizeTracker;
        private final Appendable delegate;

        private CountingAppendable(FieldSectionSizeTracker sizeTracker, Appendable delegate) {
            this.sizeTracker = sizeTracker;
            this.delegate = delegate;
        }

        @Override
        public Appendable append(CharSequence csq) throws IOException {
            CharSequence value = csq == null ? "null" : csq;
            sizeTracker.consume(value.length());
            delegate.append(value);
            return this;
        }

        @Override
        public Appendable append(CharSequence csq, int start, int end) throws IOException {
            CharSequence value = csq == null ? "null" : csq;
            sizeTracker.consume(end - start);
            delegate.append(value, start, end);
            return this;
        }

        @Override
        public Appendable append(char c) throws IOException {
            sizeTracker.consume(1);
            delegate.append(c);
            return this;
        }
    }

    record HeaderField(String name, String value) {

        HeaderField(String name) {
            this(name, "");
        }

        String text() {
            return value.isEmpty() ? name : name + ":" + value;
        }

        @Override
        public String toString() {
            return text();
        }
    }
}
