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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.HuffmanCodec;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QpackCodecTest {
    @Test
    void shouldRoundTripLiteralHeaders() {
        WritableHeaders<?> headers = WritableHeaders.create()
                .add(HeaderValues.create(HeaderNames.CONTENT_TYPE, "application/json"))
                .add(HeaderValues.create("x-test", "value"));

        Headers decoded = QpackCodec.decodeHeaders(BufferData.create(QpackCodec.encodeHeaders(headers)));

        assertThat(decoded.first(HeaderNames.CONTENT_TYPE).orElseThrow(), equalTo("application/json"));
        assertThat(decoded.first(HeaderNames.create("x-test")).orElseThrow(), equalTo("value"));
    }

    @Test
    void shouldDecodeStaticIndexedAndNameReferencedHeaders() {
        byte[] payload = concat(new byte[] {0x00, 0x00},
                                new byte[] {(byte) 0xD1},
                                new byte[] {(byte) 0x50, 0x0B},
                                "example.com".getBytes(StandardCharsets.ISO_8859_1),
                                new byte[] {0x26},
                                "x-test".getBytes(StandardCharsets.ISO_8859_1),
                                new byte[] {0x05},
                                "value".getBytes(StandardCharsets.ISO_8859_1));

        Headers decoded = QpackCodec.decodeHeaders(BufferData.create(payload));

        assertThat(decoded.first(HeaderNames.createFromLowercase(":method")).orElseThrow(), equalTo("GET"));
        assertThat(decoded.first(HeaderNames.createFromLowercase(":authority")).orElseThrow(), equalTo("example.com"));
        assertThat(decoded.first(HeaderNames.create("x-test")).orElseThrow(), equalTo("value"));
    }

    @Test
    void shouldEncodeLateExactAndFirstNameStaticReferences() {
        List<Header> headers = List.of(
                HeaderValues.create("x-frame-options", "sameorigin"),
                HeaderValues.create("x-frame-options", "custom"));
        QpackConnectionState encoder = qpackState(0, 0);

        byte[] encoded = encoder.encodeHeaders(0, headers);

        assertThat(encoded, equalTo(concat(new byte[] {0, 0, (byte) 0xff, 0x23, 0x5f, 0x52, 0x06},
                                           "custom".getBytes(StandardCharsets.ISO_8859_1))));
        List<Header> decoded = QpackCodec.decodeHeaderLines(BufferData.create(encoded));
        assertThat(decoded, hasSize(2));
        assertThat(decoded.get(0).headerName().lowerCase(), equalTo("x-frame-options"));
        assertThat(decoded.get(0).get(), equalTo("sameorigin"));
        assertThat(decoded.get(1).headerName().lowerCase(), equalTo("x-frame-options"));
        assertThat(decoded.get(1).get(), equalTo("custom"));
    }

    @Test
    void shouldRoundTripDynamicHeadersAcrossQpackContexts() throws Exception {
        Http3QpackContext encoder = qpackContext(0, 0);
        Http3QpackContext decoder = qpackContext(512, 8);

        encoder.peerSettings(512, 8);
        encoder.encoderInstructionsSender(decoder::onEncoderStreamData);
        decoder.decoderInstructionsSender(encoder::onDecoderStreamData);

        WritableHeaders<?> headers = WritableHeaders.create()
                .add(HeaderValues.create("x-user", "alpha"))
                .add(HeaderValues.create("x-env", "dev"));

        byte[] first = encoder.encodeHeaders(0, headers);
        Headers firstDecoded = decodeHeaders(decoder, 0, first, -1);

        byte[] second = encoder.encodeHeaders(4, headers);
        Headers secondDecoded = decodeHeaders(decoder, 4, second, -1);

        assertThat(firstDecoded.first(HeaderNames.create("x-user")).orElseThrow(), equalTo("alpha"));
        assertThat(firstDecoded.first(HeaderNames.create("x-env")).orElseThrow(), equalTo("dev"));
        assertThat(secondDecoded.first(HeaderNames.create("x-user")).orElseThrow(), equalTo("alpha"));
        assertThat(secondDecoded.first(HeaderNames.create("x-env")).orElseThrow(), equalTo("dev"));
        assertThat(second.length, lessThan(first.length));
    }

    @Test
    void shouldRejectLiteralHeadersThatExceedConfiguredFieldSectionSize() {
        WritableHeaders<?> headers = WritableHeaders.create()
                .add(HeaderValues.create("x-test", "value"));

        Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                        () -> QpackCodec.decodeHeaders(BufferData.create(
                                                                        QpackCodec.encodeHeaders(headers)),
                                                                42));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    void shouldDecodeMaximumHuffmanExpansionAtExactAccumulatedLimit() {
        String value = "0".repeat(160);
        byte[] encoded = new byte[HuffmanCodec.encodedLength(value)];
        assertThat(HuffmanCodec.encode(value, encoded), is(encoded.length));
        int priorFieldSize = 32 + 3 + 5;
        int currentFieldPrefixSize = 32 + 1;
        long exactLimit = priorFieldSize + currentFieldPrefixSize + value.length();
        QpackCodec.FieldSectionSizeTracker exactTracker = QpackCodec.fieldSectionSizeTracker(exactLimit);
        exactTracker.consume(priorFieldSize);
        exactTracker.beginFieldLine();
        exactTracker.consume(1);
        assertThat(exactTracker.initialStringCapacity(value.length()), equalTo(value.length()));

        String decoded = QpackCodec.decodeStringBytes(BufferData.create(encoded), true, exactTracker);

        assertThat(encoded.length, equalTo(100));
        assertThat(decoded, equalTo(value));

        QpackCodec.FieldSectionSizeTracker excessTracker = QpackCodec.fieldSectionSizeTracker(exactLimit - 1);
        excessTracker.consume(priorFieldSize);
        excessTracker.beginFieldLine();
        excessTracker.consume(1);
        assertThat(excessTracker.initialStringCapacity(value.length()), equalTo(value.length() - 1));
        Http3ProtocolException exception = assertThrows(
                Http3ProtocolException.class,
                () -> QpackCodec.decodeStringBytes(BufferData.create(encoded), true, excessTracker));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    void shouldPreserveHuffmanPaddingValidation() {
        byte[] validPadding = new byte[HuffmanCodec.encodedLength("a")];
        assertThat(HuffmanCodec.encode("a", validPadding), is(validPadding.length));

        assertThat(QpackCodec.decodeStringBytes(BufferData.create(validPadding), true), equalTo("a"));
        assertThrows(IllegalArgumentException.class,
                     () -> QpackCodec.decodeStringBytes(BufferData.create(new byte[] {(byte) 0xff}), true));
    }

    @Test
    void shouldReportSizeLimitBeforeMalformedHuffmanPaddingAfterDecodedContent() {
        byte[] malformedPaddingAfterA = {0x1e};
        assertThrows(IllegalArgumentException.class,
                     () -> QpackCodec.decodeStringBytes(BufferData.create(malformedPaddingAfterA), true));

        Http3ProtocolException exception = assertThrows(
                Http3ProtocolException.class,
                () -> QpackCodec.decodeStringBytes(BufferData.create(malformedPaddingAfterA),
                                                   true,
                                                   QpackCodec.fieldSectionSizeTracker(0)));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    void shouldDecodeEveryNonHuffmanOctet() {
        byte[] encoded = new byte[256];
        char[] expected = new char[256];
        for (int octet = 0; octet < encoded.length; octet++) {
            encoded[octet] = (byte) octet;
            expected[octet] = (char) octet;
        }
        BufferData buffer = BufferData.create(encoded);

        String decoded = QpackCodec.decodeStringBytes(buffer, false);

        assertThat(decoded, equalTo(new String(expected)));
        assertThat(buffer.available(), is(0));
    }

    @Test
    void shouldDecodeEmptyNonHuffmanValueAtZeroLimit() {
        String decoded = QpackCodec.decodeStringBytes(BufferData.empty(),
                                                      false,
                                                      QpackCodec.fieldSectionSizeTracker(0));

        assertThat(decoded, equalTo(""));
    }

    @Test
    void shouldDecodeNonHuffmanValueFromConsumedReadOnlySlice() {
        byte[] encoded = {0x11, 0x22, 'x', (byte) 0x80, (byte) 0xe9, (byte) 0xff, 0x33, 0x44};
        BufferData buffer = BufferData.createReadOnly(encoded, 2, 4);
        buffer.skip(1);

        String decoded = QpackCodec.decodeStringBytes(buffer,
                                                      false,
                                                      QpackCodec.fieldSectionSizeTracker(3));

        assertThat(decoded, equalTo("\u0080\u00e9\u00ff"));
        assertThat(buffer.available(), is(0));
    }

    @Test
    void shouldDecodeNonHuffmanValueAcrossConsumedCompositeBuffers() {
        BufferData first = BufferData.create(new byte[] {'x', (byte) 0x80});
        BufferData second = BufferData.createReadOnly(new byte[] {0x11, (byte) 0xe9, (byte) 0xff, 0x22}, 1, 2);
        BufferData buffer = BufferData.create(first, second);
        buffer.skip(1);

        String decoded = QpackCodec.decodeStringBytes(buffer,
                                                      false,
                                                      QpackCodec.fieldSectionSizeTracker(3));

        assertThat(decoded, equalTo("\u0080\u00e9\u00ff"));
        assertThat(buffer.available(), is(0));
        assertThat(first.available(), is(0));
        assertThat(second.available(), is(0));
    }

    @Test
    void shouldDecodeNonHuffmanValueAtExactAccumulatedLimit() {
        byte[] encoded = {(byte) 0x80, (byte) 0xe9, (byte) 0xff};
        int priorFieldSize = 32 + 3 + 5;
        int currentFieldPrefixSize = 32 + 1;
        long exactLimit = priorFieldSize + currentFieldPrefixSize + encoded.length;
        QpackCodec.FieldSectionSizeTracker exactTracker = QpackCodec.fieldSectionSizeTracker(exactLimit);
        exactTracker.consume(priorFieldSize);
        exactTracker.beginFieldLine();
        exactTracker.consume(1);

        String decoded = QpackCodec.decodeStringBytes(BufferData.create(encoded), false, exactTracker);

        assertThat(decoded, equalTo("\u0080\u00e9\u00ff"));

        QpackCodec.FieldSectionSizeTracker excessTracker = QpackCodec.fieldSectionSizeTracker(exactLimit - 1);
        excessTracker.consume(priorFieldSize);
        excessTracker.beginFieldLine();
        excessTracker.consume(1);
        Http3ProtocolException exception = assertThrows(
                Http3ProtocolException.class,
                () -> QpackCodec.decodeStringBytes(BufferData.create(encoded), false, excessTracker));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    void shouldDecodeAdjacentLatin1LiteralFieldsWithinAccumulatedLimit() {
        byte[] encoded = {0, 0,
                0x23, 'x', '-', 'a', 2, (byte) 0x80, (byte) 0xe9,
                0x23, 'x', '-', 'b', 2, (byte) 0xfe, (byte) 0xff};
        long exactLimit = 2 * (32 + 3 + 2);
        BufferData buffer = BufferData.create(encoded);

        List<Header> decoded = QpackCodec.decodeHeaderLines(buffer, exactLimit);

        assertThat(decoded, equalTo(List.of(HeaderValues.create("x-a", "\u0080\u00e9"),
                                            HeaderValues.create("x-b", "\u00fe\u00ff"))));
        assertThat(buffer.available(), is(0));

        Http3ProtocolException exception = assertThrows(
                Http3ProtocolException.class,
                () -> QpackCodec.decodeHeaderLines(BufferData.create(encoded), exactLimit - 1));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    void shouldRejectDynamicHeadersThatExceedConfiguredFieldSectionSize() throws Exception {
        Http3QpackContext encoder = qpackContext(0, 0);
        Http3QpackContext decoder = qpackContext(512, 8);

        encoder.peerSettings(512, 8);
        encoder.encoderInstructionsSender(decoder::onEncoderStreamData);
        decoder.decoderInstructionsSender(encoder::onDecoderStreamData);

        WritableHeaders<?> headers = WritableHeaders.create()
                .add(HeaderValues.create("x-user", "alpha"));

        byte[] first = encoder.encodeHeaders(0, headers);
        decodeHeaders(decoder, 0, first, -1);

        byte[] second = encoder.encodeHeaders(4, headers);

        Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                        () -> decodeHeaders(decoder, 4, second, 42));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    void shouldRejectNegativeFieldSectionBase() {
        assertThrows(IllegalArgumentException.class,
                     () -> QpackCodec.readFieldSectionPrefix(
                             BufferData.create(new byte[] {0, (byte) 0x80}),
                             0,
                             0));
        assertThrows(IllegalArgumentException.class,
                     () -> QpackCodec.readFieldSectionPrefix(
                             BufferData.create(new byte[] {2, (byte) 0x81}),
                             1,
                             4));
    }

    @Test
    void shouldAcceptNonZeroBaseWithoutDynamicReferences() {
        Headers decoded = QpackCodec.decodeHeaders(BufferData.create(new byte[] {0, 1, (byte) 0xd1}));

        assertThat(decoded.first(HeaderNames.createFromLowercase(":method")).orElseThrow(), equalTo("GET"));
    }

    @Test
    void shouldTrackOutstandingSectionsPerStream() throws Exception {
        QpackConnectionState encoder = qpackState(0, 0);
        QpackConnectionState decoder = qpackState(512, 8);
        AtomicReference<byte[]> capturedDecoderInstruction = new AtomicReference<>();
        AtomicReference<Http3QpackContext.InstructionSender> decoderInstructionTarget =
                new AtomicReference<>(encoder::onDecoderStreamData);

        encoder.peerSettings(512, 8);
        encoder.encoderInstructionsSender(decoder::onEncoderStreamData);
        decoder.decoderInstructionsSender(bytes -> decoderInstructionTarget.get().send(bytes));

        WritableHeaders<?> headers = WritableHeaders.create()
                .add(HeaderValues.create("x-user", "alpha"))
                .add(HeaderValues.create("x-env", "dev"));

        byte[] first = encoder.encodeHeaders(0, headers);
        QpackConnectionState.DecoderStream firstStream = decoder.openDecoderStream(0);
        firstStream.decodeHeaderLines(BufferData.create(first), -1);
        firstStream.complete();

        decoderInstructionTarget.set(capturedDecoderInstruction::set);

        byte[] second = encoder.encodeHeaders(4, headers);
        QpackConnectionState.DecoderStream secondStream = decoder.openDecoderStream(4);
        secondStream.decodeHeaderLines(BufferData.create(second), -1);
        secondStream.complete();

        encoder.onDecoderStreamData(capturedDecoderInstruction.get());
        Http3ProtocolException duplicateAcknowledgment = assertThrows(
                Http3ProtocolException.class,
                () -> encoder.onDecoderStreamData(capturedDecoderInstruction.get()));
        assertThat(duplicateAcknowledgment.errorCode(), equalTo(Http3ErrorCode.QPACK_DECODER_STREAM_ERROR));
    }

    @Test
    void shouldRejectEncoderCapacityUpdateAboveAdvertisedLimit() {
        Http3QpackContext context = qpackContext(16, 1);

        Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                        () -> context.onEncoderStreamData(encoderCapacityUpdate(32)));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.QPACK_ENCODER_STREAM_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectZeroInsertCountIncrement() {
        Http3QpackContext context = qpackContext(0, 0);

        Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                        () -> context.onDecoderStreamData(insertCountIncrement(0)));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.QPACK_DECODER_STREAM_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRoundTripLargePrefixedInteger() {
        long expected = (1L << 33) + 17;
        BufferData output = BufferData.growing(16);

        QpackCodec.writePrefixedInteger(output, 7, 0b1000_0000, expected);

        assertThat(QpackCodec.readPrefixedInteger(output.rewind(), 7), equalTo(expected));
    }

    private static Http3QpackContext qpackContext(long maxTableCapacity, long blockedStreams) {
        return Http3QpackContext.create(maxTableCapacity, blockedStreams, 16_384, _ -> {
        });
    }

    private static QpackConnectionState qpackState(long maxTableCapacity, long blockedStreams) {
        return QpackConnectionState.create(maxTableCapacity, blockedStreams, 16_384, _ -> {
        });
    }

    private static Headers decodeHeaders(Http3QpackContext context,
                                         long streamId,
                                         byte[] bytes,
                                         long maxFieldSectionSize) {
        Http3QpackContext.Stream stream = context.openStream(streamId);
        try {
            return stream.decodeHeaders(BufferData.create(bytes), maxFieldSectionSize);
        } finally {
            stream.complete();
        }
    }

    private static byte[] concat(byte[]... arrays) {
        int size = 0;
        for (byte[] array : arrays) {
            size += array.length;
        }

        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

    private static byte[] encoderCapacityUpdate(long capacity) {
        BufferData output = BufferData.growing(16);
        QpackCodec.writePrefixedInteger(output, 5, 0b0010_0000, capacity);
        return output.readBytes();
    }

    private static byte[] insertCountIncrement(long increment) {
        BufferData output = BufferData.growing(16);
        QpackCodec.writePrefixedInteger(output, 6, 0, increment);
        return output.readBytes();
    }
}
