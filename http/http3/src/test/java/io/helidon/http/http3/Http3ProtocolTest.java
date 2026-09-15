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

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.quic.VariableLengthEncoder;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3ProtocolTest {
    @Test
    void shouldNameKnownHttp3ApplicationErrors() {
        assertThat(Http3Protocol.applicationErrorToString(Http3ErrorCode.REQUEST_CANCELLED.code()),
                   equalTo("H3_REQUEST_CANCELLED"));
        assertThat(Http3Protocol.applicationErrorToString(Http3ErrorCode.ID_ERROR.code()),
                   equalTo("H3_ID_ERROR"));
        assertThat(Http3Protocol.applicationErrorToString(Http3ErrorCode.QPACK_ENCODER_STREAM_ERROR.code()),
                   equalTo("QPACK_ENCODER_STREAM_ERROR"));
        assertThat(Http3Protocol.applicationErrorToString(0x1234),
                   equalTo("ApplicationError(code=0x0000000000001234)"));
    }

    @Test
    void shouldRoundTripHttp3Request() throws Exception {
        URI uri = URI.create("https://example.com/hello?name=test");
        WritableHeaders<?> headers = WritableHeaders.create()
                .add(HeaderValues.create(HeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8"))
                .add(HeaderValues.create("x-request-id", "abc-123"));
        byte[] encoded = concat(Http3Protocol.encodeRequestHeaders(qpackContext(0, 0),
                                                                   0,
                                                                   uri,
                                                                   "POST",
                                                                   headers),
                                Http3Protocol.encodeDataFrame("payload".getBytes(StandardCharsets.UTF_8)));
        ByteBuffer encodedBuffer = ByteBuffer.wrap(encoded);
        byte[] requestHeadersPayload = nextFramePayload(encodedBuffer, Http3Protocol.FRAME_HEADERS);
        byte[] requestBody = nextFramePayload(encodedBuffer, Http3Protocol.FRAME_DATA);
        Http3Protocol.DecodedRequestHead decoded = decodeRequestHeaders(requestHeadersPayload);

        assertThat(decoded.method(), equalTo("POST"));
        assertThat(decoded.scheme().orElseThrow(), equalTo("https"));
        assertThat(decoded.authority(), equalTo("example.com"));
        assertThat(decoded.parsedAuthority().toString(), equalTo("example.com"));
        assertThat(decoded.path().orElseThrow(), equalTo("/hello?name=test"));
        assertThat(decoded.headers().first(HeaderNames.CONTENT_TYPE).orElseThrow(),
                   equalTo("text/plain; charset=utf-8"));
        assertThat(decoded.headers().first(HeaderNames.create("x-request-id")).orElseThrow(), equalTo("abc-123"));
        assertThat(new String(requestBody, StandardCharsets.UTF_8), equalTo("payload"));
    }

    @Test
    void shouldUseHostHeaderAsRequestAuthority() throws Exception {
        WritableHeaders<?> headers = WritableHeaders.create()
                .add(HeaderValues.create(HeaderNames.HOST, "tenant.example:8443"));
        byte[] encoded = Http3Protocol.encodeRequestHeaders(qpackContext(0, 0),
                                                            0,
                                                            URI.create("https://origin.example/hello"),
                                                            "GET",
                                                            headers);
        byte[] requestHeadersPayload = nextFramePayload(ByteBuffer.wrap(encoded), Http3Protocol.FRAME_HEADERS);

        Http3Protocol.DecodedRequestHead decoded = decodeRequestHeaders(requestHeadersPayload);

        assertThat(decoded.authority(), equalTo("tenant.example:8443"));
        assertThat(decoded.parsedAuthority().toString(), equalTo("tenant.example:8443"));
        assertThat(decoded.headers().contains(HeaderNames.HOST), is(false));
    }

    @Test
    void shouldPreserveRawAuthorityAndRetainItsParsedRepresentation() {
        String rawAuthority = "Api.Example.COM:8443";
        byte[] payload = QpackCodec.encodeHeaders(List.of(HeaderValues.create(":method", "GET"),
                                                          HeaderValues.create(":scheme", "https"),
                                                          HeaderValues.create(":authority", rawAuthority),
                                                          HeaderValues.create(":path", "/")));

        Http3Protocol.DecodedRequestHead decoded = decodeRequestHeaders(payload);

        assertThat(decoded.authority(), equalTo(rawAuthority));
        assertThat(decoded.parsedAuthority().toString(), equalTo("api.example.com:8443"));
        assertThat(decoded.parsedAuthority().port(), equalTo(8443));
    }

    @Test
    void shouldRejectMissingRequestAuthority() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Http3Protocol.requestAuthority(URI.create("/relative"), WritableHeaders.create()));

        assertThat(exception.getMessage(), equalTo("HTTP/3 request authority must not be empty"));
    }

    @Test
    void shouldEncodeClassicConnectWithoutSchemeOrPath() throws Exception {
        byte[] encoded = Http3Protocol.encodeRequestHeaders(qpackContext(0, 0),
                                                            0,
                                                            URI.create("https://example.com:443"),
                                                            "CONNECT",
                                                            WritableHeaders.create()
                                                                    .add(HeaderValues.create(HeaderNames.HOST,
                                                                                             "example.com:443")));

        Http3Protocol.DecodedRequestHead decoded = decodeRequestHeaders(
                nextFramePayload(ByteBuffer.wrap(encoded), Http3Protocol.FRAME_HEADERS));

        assertThat(decoded.method(), equalTo("CONNECT"));
        assertThat(decoded.scheme().isEmpty(), is(true));
        assertThat(decoded.authority(), equalTo("example.com:443"));
        assertThat(decoded.parsedAuthority().toString(), equalTo("example.com:443"));
        assertThat(decoded.path().isEmpty(), is(true));
        assertThat(decoded.headers().contains(HeaderNames.HOST), is(false));
    }

    @Test
    void shouldTreatLowercaseConnectAsOrdinaryMethod() throws Exception {
        byte[] encoded = Http3Protocol.encodeRequestHeaders(qpackContext(0, 0),
                                                            0,
                                                            URI.create("https://example.com/resource"),
                                                            "connect",
                                                            WritableHeaders.create());

        Http3Protocol.DecodedRequestHead decoded = decodeRequestHeaders(
                nextFramePayload(ByteBuffer.wrap(encoded), Http3Protocol.FRAME_HEADERS));

        assertThat(decoded.method(), equalTo("connect"));
        assertThat(decoded.scheme().orElseThrow(), equalTo("https"));
        assertThat(decoded.authority(), equalTo("example.com"));
        assertThat(decoded.parsedAuthority().toString(), equalTo("example.com"));
        assertThat(decoded.path().orElseThrow(), equalTo("/resource"));
    }

    @Test
    void shouldRequireExplicitConnectAuthorityPort() {
        assertThrows(IllegalArgumentException.class,
                     () -> Http3Protocol.encodeRequestHeaders(qpackContext(0, 0),
                                                              0,
                                                              URI.create("https://example.com"),
                                                              "CONNECT",
                                                              WritableHeaders.create()));

        byte[] payload = QpackCodec.encodeHeaders(List.of(HeaderValues.create(":method", "CONNECT"),
                                                          HeaderValues.create(":authority", "example.com")));
        Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                        () -> decodeRequestHeaders(payload));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    void shouldRejectZeroConnectAuthorityPort() {
        assertThrows(IllegalArgumentException.class,
                     () -> Http3Protocol.encodeRequestHeaders(qpackContext(0, 0),
                                                              0,
                                                              URI.create("https://example.com:0"),
                                                              "CONNECT",
                                                              WritableHeaders.create()));

        byte[] payload = QpackCodec.encodeHeaders(List.of(HeaderValues.create(":method", "CONNECT"),
                                                          HeaderValues.create(":authority", "example.com:0")));
        Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                        () -> decodeRequestHeaders(payload));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    void shouldRejectConflictingHostAndAuthority() {
        byte[] payload = QpackCodec.encodeHeaders(List.of(HeaderValues.create(":method", "GET"),
                                                          HeaderValues.create(":scheme", "https"),
                                                          HeaderValues.create(":authority", "example.com"),
                                                          HeaderValues.create(":path", "/"),
                                                          HeaderValues.create(HeaderNames.HOST, "other.example")));

        Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                        () -> decodeRequestHeaders(payload));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    void shouldDecodeResponseTrailersSeparately() {
        byte[] encoded = concat(Http3Protocol.encodeResponseHeaders(200,
                                                                    WritableHeaders.create()
                                                                            .add(HeaderValues.create(HeaderNames.CONTENT_TYPE,
                                                                                                     "text/plain"))),
                                Http3Protocol.encodeDataFrame("hello".getBytes(StandardCharsets.UTF_8)),
                                Http3Protocol.encodeHeadersFrame(WritableHeaders.create()
                                                                         .add(HeaderValues.create("x-trailer", "done"))));

        DecodedResponse decoded = decodeResponseMessage(encoded);

        assertThat(decoded.status(), equalTo(200));
        assertThat(decoded.headers().first(HeaderNames.CONTENT_TYPE).orElseThrow(), equalTo("text/plain"));
        assertThat(new String(decoded.body(), StandardCharsets.UTF_8), equalTo("hello"));
        assertThat(decoded.trailers().first(HeaderNames.create("x-trailer")).orElseThrow(), equalTo("done"));
    }

    @Test
    void shouldDetectCompleteControlStreamSettings() {
        Http3Settings expected = Http3Settings.create(OptionalLong.of(4096),
                                                      128,
                                                      8,
                                                      Map.of(0x21L, 42L));
        byte[] settings = Http3Protocol.controlStreamPreamble(expected);

        assertThat(hasCompleteControlStreamSettings(settings), is(true));
        assertThat(hasCompleteControlStreamSettings(Arrays.copyOf(settings, settings.length - 1)), is(false));
        assertThat(decodeUniStreamType(settings), equalTo(Http3StreamType.CONTROL.code()));
        Http3Settings decoded = Http3Protocol.decodeSettingsPayload(controlStreamSettingsPayload(settings));
        assertThat(decoded, equalTo(expected));
        assertThat(decoded.extensionSettings(), equalTo(Map.of(0x21L, 42L)));
        assertThrows(UnsupportedOperationException.class,
                     () -> decoded.extensionSettings().put(0x22L, 43L));
    }

    @Test
    void shouldCreateValidatedSettingsValues() {
        Http3Settings settings = Http3Settings.create(128, 8);

        assertThat(settings.maxFieldSectionSize(), equalTo(OptionalLong.empty()));
        assertThat(settings.qpackMaxTableCapacity(), equalTo(128L));
        assertThat(settings.qpackBlockedStreams(), equalTo(8L));
        assertThat(Http3Settings.create(4096, 128, 8).maxFieldSectionSize(),
                   equalTo(OptionalLong.of(4096)));

        long tooLarge = VariableLengthEncoder.MAX_ENCODED_INTEGER + 1;
        assertThrows(IllegalArgumentException.class, () -> Http3Settings.create(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> Http3Settings.create(0, -1));
        assertThrows(IllegalArgumentException.class, () -> Http3Settings.create(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Http3Settings.create(tooLarge, 0));
        assertThrows(IllegalArgumentException.class, () -> Http3Settings.create(0, tooLarge));
        assertThrows(IllegalArgumentException.class, () -> Http3Settings.create(tooLarge, 0, 0));
    }

    @Test
    void shouldCreateSettingsFromConfiguredBoundaries() {
        long maxVarInt = VariableLengthEncoder.MAX_ENCODED_INTEGER;

        Http3Settings unconfigured = Http3Settings.createConfigured(-1, -1, -1);
        assertThat(unconfigured.maxFieldSectionSize(), equalTo(OptionalLong.empty()));
        assertThat(unconfigured.qpackMaxTableCapacity(), equalTo(0L));
        assertThat(unconfigured.qpackBlockedStreams(), equalTo(0L));

        Http3Settings zero = Http3Settings.createConfigured(0, 0, 0);
        assertThat(zero.maxFieldSectionSize(), equalTo(OptionalLong.of(0)));
        assertThat(zero.qpackMaxTableCapacity(), equalTo(0L));
        assertThat(zero.qpackBlockedStreams(), equalTo(0L));

        Http3Settings maximum = Http3Settings.createConfigured(maxVarInt, maxVarInt, maxVarInt);
        assertThat(maximum.maxFieldSectionSize(), equalTo(OptionalLong.of(maxVarInt)));
        assertThat(maximum.qpackMaxTableCapacity(), equalTo(maxVarInt));
        assertThat(maximum.qpackBlockedStreams(), equalTo(maxVarInt));

        long tooLarge = maxVarInt + 1;
        assertThrows(IllegalArgumentException.class, () -> Http3Settings.createConfigured(-2, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Http3Settings.createConfigured(0, -2, 0));
        assertThrows(IllegalArgumentException.class, () -> Http3Settings.createConfigured(0, 0, -2));
        assertThrows(IllegalArgumentException.class, () -> Http3Settings.createConfigured(tooLarge, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Http3Settings.createConfigured(0, tooLarge, 0));
        assertThrows(IllegalArgumentException.class, () -> Http3Settings.createConfigured(0, 0, tooLarge));
    }

    @Test
    void shouldRejectInvalidExtensionSettings() {
        for (long settingId : List.of(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L)) {
            assertThrows(IllegalArgumentException.class,
                         () -> Http3Settings.create(OptionalLong.empty(), 0, 0, Map.of(settingId, 1L)));
        }

        long tooLarge = VariableLengthEncoder.MAX_ENCODED_INTEGER + 1;
        assertThrows(IllegalArgumentException.class,
                     () -> Http3Settings.create(OptionalLong.empty(), 0, 0, Map.of(-1L, 1L)));
        assertThrows(IllegalArgumentException.class,
                     () -> Http3Settings.create(OptionalLong.empty(), 0, 0, Map.of(tooLarge, 1L)));
        assertThrows(IllegalArgumentException.class,
                     () -> Http3Settings.create(OptionalLong.empty(), 0, 0, Map.of(0x21L, -1L)));
        assertThrows(IllegalArgumentException.class,
                     () -> Http3Settings.create(OptionalLong.empty(), 0, 0, Map.of(0x21L, tooLarge)));
    }

    @Test
    void shouldRoundTripQpackEncodedResponseHeaders() throws Exception {
        Http3QpackContext server = qpackContext(0, 0);
        Http3QpackContext client = qpackContext(512, 8);

        server.peerSettings(512, 8);
        server.encoderInstructionsSender(client::onEncoderStreamData);
        client.decoderInstructionsSender(server::onDecoderStreamData);

        WritableHeaders<?> headers = WritableHeaders.create()
                .add(HeaderValues.create(HeaderNames.CONTENT_TYPE, "application/json"))
                .add(HeaderValues.create("x-user", "alpha"));

        byte[] firstFrame = Http3Protocol.encodeResponseHeaders(server, 0, 200, headers);
        DecodedResponseHead firstDecoded = decodeResponseHead(client,
                                                              0,
                                                              framePayload(firstFrame, Http3Protocol.FRAME_HEADERS));

        byte[] secondFrame = Http3Protocol.encodeResponseHeaders(server, 4, 200, headers);
        DecodedResponseHead secondDecoded = decodeResponseHead(client,
                                                               4,
                                                               framePayload(secondFrame, Http3Protocol.FRAME_HEADERS));

        assertThat(firstDecoded.status(), equalTo(200));
        assertThat(firstDecoded.headers().first(HeaderNames.CONTENT_TYPE).orElseThrow(), equalTo("application/json"));
        assertThat(firstDecoded.headers().first(HeaderNames.create("x-user")).orElseThrow(), equalTo("alpha"));
        assertThat(secondDecoded.status(), equalTo(200));
        assertThat(secondDecoded.headers().first(HeaderNames.create("x-user")).orElseThrow(), equalTo("alpha"));
        assertThat(secondFrame.length, lessThan(firstFrame.length));
    }

    @Test
    //= https://www.rfc-editor.org/rfc/rfc9114#section-4.3
    //# Endpoints MUST treat a request or response that contains undefined or
    //# invalid pseudo-header fields as malformed.
    //= https://www.rfc-editor.org/rfc/rfc9114#section-4.3.1
    //# All HTTP/3 requests MUST include exactly one value for the :method,
    //# :scheme, and :path pseudo-header fields, unless the request is a
    //# CONNECT request; see Section 4.4.
    //= https://www.rfc-editor.org/rfc/rfc9114#section-4.3.1
    //# An HTTP request that omits mandatory pseudo-header fields or contains
    //# invalid values for those pseudo-header fields is malformed.
    void shouldRejectDuplicateRequestPseudoHeaders() {
        byte[] payload = QpackCodec.encodeHeaders(List.of(HeaderValues.create(":method", "GET"),
                                                          HeaderValues.create(":method", "POST"),
                                                          HeaderValues.create(":scheme", "https"),
                                                          HeaderValues.create(":authority", "example.com"),
                                                          HeaderValues.create(":path", "/")));

        Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                        () -> decodeRequestHeaders(payload));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    //= https://www.rfc-editor.org/rfc/rfc9114#section-4.3
    //# All pseudo-header fields MUST appear in the header section before
    //# regular header fields.
    //# Any request or response that contains a pseudo-header field that
    //# appears in a header section after a regular header field MUST be
    //# treated as malformed.
    void shouldRejectPseudoHeadersAfterRegularHeaders() {
        byte[] payload = QpackCodec.encodeHeaders(List.of(HeaderValues.create(":method", "GET"),
                                                          HeaderValues.create(":scheme", "https"),
                                                          HeaderValues.create("x-test", "value"),
                                                          HeaderValues.create(":authority", "example.com"),
                                                          HeaderValues.create(":path", "/")));

        Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                        () -> decodeRequestHeaders(payload));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    //= https://www.rfc-editor.org/rfc/rfc9114#section-4.3
    //# Endpoints MUST treat a request or response that contains undefined or
    //# invalid pseudo-header fields as malformed.
    void shouldRejectProhibitedRequestPseudoHeaders() {
        byte[] payload = QpackCodec.encodeHeaders(List.of(HeaderValues.create(":method", "GET"),
                                                          HeaderValues.create(":scheme", "https"),
                                                          HeaderValues.create(":authority", "example.com"),
                                                          HeaderValues.create(":path", "/"),
                                                          HeaderValues.create(":status", "200")));

        Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                        () -> decodeRequestHeaders(payload));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    //= https://www.rfc-editor.org/rfc/rfc9114#section-4.2
    //# A request or response containing uppercase characters in field names
    //# MUST be treated as malformed.
    //= https://www.rfc-editor.org/rfc/rfc9114#section-4.1.2
    //# Malformed requests or responses that are detected MUST be treated as a
    //# stream error of type H3_MESSAGE_ERROR.
    void shouldRejectUppercaseRequestHeaderNames() {
        byte[] payload = qpackLiteralHeadersPayload(List.of(HeaderValues.create(":method", "GET"),
                                                            HeaderValues.create(":scheme", "https"),
                                                            HeaderValues.create(":authority", "example.com"),
                                                            HeaderValues.create(":path", "/"),
                                                            HeaderValues.create(HeaderNames.create("X-Test"), "value")));

        Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                        () -> decodeRequestHeaders(payload));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    void shouldRejectRequestHeadersThatExceedConfiguredFieldSectionSize() {
        byte[] payload = qpackLiteralHeadersPayload(List.of(HeaderValues.create(":method", "GET"),
                                                            HeaderValues.create(":scheme", "https"),
                                                            HeaderValues.create(":authority", "example.com"),
                                                            HeaderValues.create(":path", "/"),
                                                            HeaderValues.create("x-test", "value")));

        Http3QpackContext.Stream qpackStream = qpackContext(0, 0).openStream(0);
        Http3ProtocolException exception;
        try {
            exception = assertThrows(Http3ProtocolException.class,
                                     () -> Http3Protocol.decodeRequestHeaders(
                                             qpackStream.decodeHeaderLines(BufferData.create(payload), 128)));
        } finally {
            qpackStream.complete();
        }

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    //= https://www.rfc-editor.org/rfc/rfc9114#section-7.2.4.1
    //# These reserved settings MUST NOT be sent, and their receipt MUST be
    //# treated as a connection error of type H3_SETTINGS_ERROR.
    void shouldRejectReservedHttp2SettingsInHttp3SettingsFrame() {
        for (long settingId : List.of(0L, 0x02L, 0x03L, 0x04L, 0x05L)) {
            Http3ProtocolException exception = assertThrows(
                    Http3ProtocolException.class,
                    () -> Http3Protocol.decodeSettingsPayload(settingsPayload(settingId, 1)));

            assertThat(exception.errorCode(), equalTo(Http3ErrorCode.SETTINGS_ERROR));
            assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
        }
    }

    @Test
    void shouldRejectDuplicateSettings() {
        for (long settingId : List.of(Http3Settings.QPACK_MAX_TABLE_CAPACITY_ID, 0x21L)) {
            Http3ProtocolException exception = assertThrows(
                    Http3ProtocolException.class,
                    () -> Http3Protocol.decodeSettingsPayload(settingsPayload(settingId, 1, settingId, 2)));

            assertThat(exception.errorCode(), equalTo(Http3ErrorCode.SETTINGS_ERROR));
            assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
        }
    }

    @Test
    void shouldRejectMalformedSettingsAsFrameError() {
        for (byte[] payload : List.of(new byte[] {(byte) 0x40},
                                      new byte[] {0x01, (byte) 0x40})) {
            Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                            () -> Http3Protocol.decodeSettingsPayload(payload));

            assertThat(exception.errorCode(), equalTo(Http3ErrorCode.FRAME_ERROR));
            assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
        }
    }

    @Test
    void shouldAcceptMaximumSettingsEntryCount() {
        Http3Settings settings = Http3Protocol.decodeSettingsPayload(settingsPayloadEntries(256));

        assertThat(settings.extensionSettings().size(), equalTo(256));
    }

    @Test
    void shouldRejectExcessiveSettingsEntryCount() {
        Http3ProtocolException exception = assertThrows(
                Http3ProtocolException.class,
                () -> Http3Protocol.decodeSettingsPayload(settingsPayloadEntries(257)));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.EXCESSIVE_LOAD));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldReportMalformedSettingBeforeApplyingEntryLimit() {
        BufferData payload = BufferData.growing(1_024);
        payload.write(settingsPayloadEntries(256));
        payload.write((byte) 0x40);

        Http3ProtocolException exception = assertThrows(
                Http3ProtocolException.class,
                () -> Http3Protocol.decodeSettingsPayload(payload.readBytes()));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.FRAME_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectInvalidStaticTableIndexInRequestHeaders() {
        byte[] payload = concat(new byte[] {0x00, 0x00}, qpackIndexedStaticFieldLine(200));

        Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                        () -> decodeRequestHeaders(payload));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.QPACK_DECOMPRESSION_FAILED));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldEncodeAndDecodeGoAwayFrame() {
        Http3GoAway expected = Http3GoAway.requestStream(1232);
        byte[] frame = Http3Protocol.goAwayFrame(expected);
        ByteBuffer buffer = ByteBuffer.wrap(frame);

        long frameType = VariableLengthEncoder.decode(buffer);
        long frameLength = VariableLengthEncoder.decode(buffer);
        byte[] payload = new byte[(int) frameLength];
        buffer.get(payload);

        assertThat(frameType, equalTo(Http3Protocol.FRAME_GOAWAY));
        assertThat(Http3Protocol.decodeGoAway(payload, Http3GoAway.Type.REQUEST_STREAM_ID), equalTo(expected));

        Http3GoAway pushId = Http3GoAway.pushId(1234);
        assertThat(Http3Protocol.decodeGoAway(framePayload(Http3Protocol.goAwayFrame(pushId),
                                                          Http3Protocol.FRAME_GOAWAY),
                                                Http3GoAway.Type.PUSH_ID),
                   equalTo(pushId));
    }

    @Test
    void shouldValidateGoAwayValues() {
        Http3GoAway goAway = Http3GoAway.requestStream(8);

        assertThat(goAway.identifier(), equalTo(8L));
        assertThat(goAway.type(), equalTo(Http3GoAway.Type.REQUEST_STREAM_ID));
        assertThat(goAway.rejectsStream(4), is(false));
        assertThat(goAway.rejectsStream(8), is(true));
        assertThat(goAway.rejectsStream(12), is(true));
        assertThat(Http3GoAway.pushId(8).rejectsStream(8), is(false));
        assertThat(Http3GoAway.requestStream(4).isValidSuccessorOf(goAway), is(true));
        assertThat(Http3GoAway.requestStream(8).isValidSuccessorOf(goAway), is(true));
        assertThat(Http3GoAway.requestStream(12).isValidSuccessorOf(goAway), is(false));
        assertThat(Http3GoAway.pushId(4).isValidSuccessorOf(goAway), is(false));

        long tooLarge = VariableLengthEncoder.MAX_ENCODED_INTEGER + 1;
        for (long invalidRequestStreamId : List.of(-1L, 1L, 2L, 3L, tooLarge)) {
            assertThrows(IllegalArgumentException.class,
                         () -> Http3GoAway.requestStream(invalidRequestStreamId));
        }
        assertThrows(IllegalArgumentException.class, () -> Http3GoAway.pushId(-1));
        assertThrows(IllegalArgumentException.class, () -> Http3GoAway.pushId(tooLarge));
    }

    @Test
    void shouldUseProtocolErrorsForInvalidGoAwayPayloads() {
        for (byte[] malformed : List.of(BufferData.EMPTY_BYTES,
                                        new byte[] {(byte) 0x40},
                                        new byte[] {0, 0})) {
            Http3ProtocolException exception = assertThrows(
                    Http3ProtocolException.class,
                    () -> Http3Protocol.decodeGoAway(malformed, Http3GoAway.Type.REQUEST_STREAM_ID));

            assertThat(exception.errorCode(), equalTo(Http3ErrorCode.FRAME_ERROR));
            assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
        }

        Http3ProtocolException exception = assertThrows(
                Http3ProtocolException.class,
                () -> Http3Protocol.decodeGoAway(settingsPayload(1), Http3GoAway.Type.REQUEST_STREAM_ID));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.ID_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldEncodeHttp3VarIntsWithoutByteBufferScratch() {
        long[] values = {0, 63, 64, 16383, 16384, (1L << 30) - 1, 1L << 30, VariableLengthEncoder.MAX_ENCODED_INTEGER};

        for (long value : values) {
            BufferData output = BufferData.create(VariableLengthEncoder.encodedSize(value));
            VariableLengthEncoder.encode(output, value);

            ByteBuffer buffer = ByteBuffer.wrap(output.readBytes());
            assertThat(VariableLengthEncoder.decode(buffer), equalTo(value));
            assertThat(buffer.hasRemaining(), is(false));
        }
    }

    @Test
    void shouldEncodeDataFrameSlicesWithoutCopyingPayloadShape() {
        byte[] payload = "prefix-body-suffix".getBytes(StandardCharsets.UTF_8);
        byte[] frame = Http3Protocol.encodeDataFrame(payload, 7, 4);

        assertThat(framePayload(frame, Http3Protocol.FRAME_DATA), equalTo("body".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldBorrowDataFrameBufferSliceUntilConsumption() {
        byte[] payload = "prefix-body-suffix".getBytes(StandardCharsets.UTF_8);
        BufferData frame = Http3Protocol.encodeDataFrameBuffer(payload, 7, 4);
        payload[7] = 'B';

        assertThat(framePayload(frame.readBytes(), Http3Protocol.FRAME_DATA),
                   equalTo("Body".getBytes(StandardCharsets.UTF_8)));
        assertThat(frame.consumed(), is(true));
    }

    @Test
    void shouldBorrowWholeDataFrameBufferUntilConsumption() {
        byte[] payload = "body".getBytes(StandardCharsets.UTF_8);
        BufferData frame = Http3Protocol.encodeDataFrameBuffer(payload, 0, payload.length);
        payload[1] = 'A';

        assertThat(framePayload(frame.readBytes(), Http3Protocol.FRAME_DATA),
                   equalTo("bAdy".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldKeepByteArrayDataFrameIndependent() {
        byte[] payload = "body".getBytes(StandardCharsets.UTF_8);
        byte[] frame = Http3Protocol.encodeDataFrame(payload);
        Arrays.fill(payload, (byte) 'x');

        assertThat(framePayload(frame, Http3Protocol.FRAME_DATA), equalTo("body".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldEncodeEmptyDataFrameBuffer() {
        BufferData frame = Http3Protocol.encodeDataFrameBuffer(new byte[0], 0, 0);

        assertThat(framePayload(frame.readBytes(), Http3Protocol.FRAME_DATA), equalTo(new byte[0]));
        assertThat(frame.consumed(), is(true));
    }

    @Test
    void shouldValidateDataFrameBufferSlice() {
        byte[] payload = new byte[4];

        assertThrows(NullPointerException.class, () -> Http3Protocol.encodeDataFrameBuffer(null, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> Http3Protocol.encodeDataFrameBuffer(payload, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> Http3Protocol.encodeDataFrameBuffer(payload, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> Http3Protocol.encodeDataFrameBuffer(payload, 2, 3));
    }

    private static byte[] concat(byte[]... arrays) {
        int size = 0;
        for (byte[] array : arrays) {
            size += array.length;
        }
        BufferData output = BufferData.create(size);
        for (byte[] array : arrays) {
            output.write(array);
        }
        return output.readBytes();
    }

    private static byte[] settingsPayload(long... values) {
        BufferData output = BufferData.growing(values.length * Long.BYTES);
        for (long value : values) {
            VariableLengthEncoder.encode(output, value);
        }
        return output.readBytes();
    }

    private static byte[] settingsPayloadEntries(int count) {
        BufferData output = BufferData.growing(count * 4);
        for (int i = 0; i < count; i++) {
            VariableLengthEncoder.encode(output, 0x100L + i);
            VariableLengthEncoder.encode(output, i);
        }
        return output.readBytes();
    }

    private static byte[] qpackIndexedStaticFieldLine(long index) {
        BufferData output = BufferData.growing(16);
        writeQpackPrefixedInteger(output, 6, 0b1100_0000, index);
        return output.readBytes();
    }

    private static byte[] qpackLiteralHeadersPayload(List<Header> headers) {
        BufferData output = BufferData.growing(128);
        output.write(0);
        output.write(0);
        for (Header header : headers) {
            writeQpackLiteralString(output, 3, 0b0010_0000, header.name());
            writeQpackLiteralString(output, 7, 0, header.get());
        }
        return output.readBytes();
    }

    private static void writeQpackLiteralString(BufferData output,
                                                int prefixBits,
                                                int leadingBits,
                                                String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        writeQpackPrefixedInteger(output, prefixBits, leadingBits, bytes.length);
        output.write(bytes);
    }

    private static void writeQpackPrefixedInteger(BufferData output,
                                                  int prefixBits,
                                                  int leadingBits,
                                                  long value) {
        int mask = (1 << prefixBits) - 1;
        if (value < mask) {
            output.write(leadingBits | (int) value);
            return;
        }
        output.write(leadingBits | mask);
        long remaining = value - mask;
        while (remaining >= 128) {
            output.write((int) ((remaining & 0x7f) | 0x80));
            remaining >>>= 7;
        }
        output.write((int) remaining);
    }

    private static byte[] controlStreamSettingsPayload(byte[] controlStreamPreamble) {
        ByteBuffer buffer = ByteBuffer.wrap(controlStreamPreamble);
        assertThat(VariableLengthEncoder.decode(buffer), equalTo(Http3StreamType.CONTROL.code()));
        return framePayload(buffer, Http3Protocol.FRAME_SETTINGS);
    }

    private static byte[] framePayload(byte[] frame, long expectedFrameType) {
        return framePayload(ByteBuffer.wrap(frame), expectedFrameType);
    }

    private static byte[] framePayload(ByteBuffer buffer, long expectedFrameType) {
        long frameType = VariableLengthEncoder.decode(buffer);
        long frameLength = VariableLengthEncoder.decode(buffer);

        assertThat(frameType, equalTo(expectedFrameType));
        assertThat(frameLength, is((long) buffer.remaining()));

        byte[] payload = new byte[(int) frameLength];
        buffer.get(payload);
        return payload;
    }

    private static byte[] nextFramePayload(ByteBuffer buffer, long expectedFrameType) {
        long frameType = VariableLengthEncoder.decode(buffer);
        long frameLength = VariableLengthEncoder.decode(buffer);

        assertThat(frameType, equalTo(expectedFrameType));
        assertThat(frameLength <= buffer.remaining(), is(true));

        byte[] payload = new byte[(int) frameLength];
        buffer.get(payload);
        return payload;
    }

    private static Http3Protocol.DecodedRequestHead decodeRequestHeaders(byte[] payload) {
        Http3QpackContext.Stream qpackStream = qpackContext(0, 0).openStream(0);
        try {
            return Http3Protocol.decodeRequestHeaders(qpackStream.decodeHeaderLines(BufferData.create(payload), -1));
        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to decode request headers payload.", e);
        } finally {
            qpackStream.complete();
        }
    }

    private static boolean hasCompleteControlStreamSettings(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long streamType = VariableLengthEncoder.decode(buffer);
        if (streamType != Http3StreamType.CONTROL.code()) {
            return false;
        }
        long frameType = VariableLengthEncoder.decode(buffer);
        long frameLength = VariableLengthEncoder.decode(buffer);
        return frameType == Http3Protocol.FRAME_SETTINGS && frameLength >= 0 && buffer.remaining() >= frameLength;
    }

    private static long decodeUniStreamType(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return VariableLengthEncoder.decode(buffer);
    }

    private static DecodedResponse decodeResponseMessage(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        BufferData body = BufferData.growing(256);
        WritableHeaders<?> trailers = WritableHeaders.create();
        DecodedResponseHead responseHead = null;
        while (buffer.hasRemaining()) {
            long frameType = VariableLengthEncoder.decode(buffer);
            long frameLength = VariableLengthEncoder.decode(buffer);
            byte[] payload = new byte[(int) frameLength];
            buffer.get(payload);
            if (frameType == Http3Protocol.FRAME_HEADERS) {
                if (responseHead == null) {
                    responseHead = decodeResponseHead(qpackContext(0, 0), 0, payload);
                } else {
                    Http3Protocol.decodeHeadersPayload(payload).forEach(trailers::add);
                }
            } else if (frameType == Http3Protocol.FRAME_DATA) {
                body.write(payload);
            }
        }
        if (responseHead == null) {
            throw new IllegalStateException("Missing HTTP/3 response headers.");
        }
        return new DecodedResponse(responseHead.status(), responseHead.headers(), body.readBytes(), trailers);
    }

    private static DecodedResponseHead decodeResponseHead(Http3QpackContext qpackContext,
                                                          long streamId,
                                                          byte[] payload) {
        Http3QpackContext.Stream qpackStream = qpackContext.openStream(streamId);
        try {
            Headers decodedHeaders = qpackStream.decodeHeaders(BufferData.create(payload), -1);
            int status = -1;
            WritableHeaders<?> headers = WritableHeaders.create();
            for (Header header : decodedHeaders) {
                if (header.headerName().lowerCase().equals(":status")) {
                    status = Integer.parseInt(header.get());
                } else {
                    headers.add(header);
                }
            }
            if (status < 0) {
                throw new IllegalArgumentException("Missing :status pseudo-header");
            }
            return new DecodedResponseHead(status, headers);
        } finally {
            qpackStream.complete();
        }
    }

    private static Http3QpackContext qpackContext(long maxTableCapacity, long blockedStreams) {
        return Http3QpackContext.create(maxTableCapacity, blockedStreams, 16_384, _ -> {
        });
    }

    private record DecodedResponse(int status, Headers headers, byte[] body, Headers trailers) {
    }

    private record DecodedResponseHead(int status, Headers headers) {
        private DecodedResponseHead {
            headers = WritableHeaders.create(headers);
        }
    }
}
