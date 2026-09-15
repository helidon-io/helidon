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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicStream;
import io.helidon.quic.stream.QuicStreamReader;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3MessageReaderTest {
    private static final Http3FrameListener NO_OP_FRAME_LISTENER = Http3FrameListener.create(List.of());

    @Test
    void shouldDeliverInformationalResponsesWithoutMergingTheirFields() throws Exception {
        Http3MessageReader reader = responseReader(Method.GET,
                                                   headersFrame(HeaderValues.create(":status", "103"),
                                                                HeaderValues.create("x-stage", "early")),
                                                   headersFrame(HeaderValues.create(":status", "100"),
                                                                HeaderValues.create("x-stage", "continue")),
                                                   headersFrame(HeaderValues.create(":status", "200"),
                                                                HeaderValues.create("x-stage", "final")));
        List<Http3MessageReader.ResponseHead> informationals = new ArrayList<>();

        Http3MessageReader.ResponseHead finalHead = reader.readResponseHead(informationals::add);

        assertThat(informationals.stream().map(it -> it.status().code()).toList(), is(List.of(103, 100)));
        assertThat(informationals.getFirst().headers().first(HeaderNames.create("x-stage")), is(Optional.of("early")));
        assertThat(informationals.getLast().headers().first(HeaderNames.create("x-stage")), is(Optional.of("continue")));
        assertThat(finalHead.status().code(), is(200));
        assertThat(finalHead.headers().first(HeaderNames.create("x-stage")), is(Optional.of("final")));
    }

    @Test
    void shouldRejectDataBeforeFinalResponseHeaders() {
        Http3MessageReader reader = responseReader(Method.GET,
                                                   Http3Protocol.encodeDataFrame(new byte[] {'x'}),
                                                   headersFrame(HeaderValues.create(":status", "200")));

        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                      () -> reader.readResponseHead(_ -> {
                                                      }));

        assertThat(failure.errorCode(), is(Http3ErrorCode.FRAME_UNEXPECTED));
        assertThat(failure.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectTruncatedFrameTypeLengthAndPayload() {
        List<byte[]> truncatedFrames = List.of(
                new byte[] {0x40},
                new byte[] {(byte) Http3Protocol.FRAME_HEADERS},
                new byte[] {(byte) Http3Protocol.FRAME_HEADERS, 1});

        for (byte[] truncatedFrame : truncatedFrames) {
            Http3MessageReader reader = responseReader(Method.GET, truncatedFrame);

            Http3ProtocolException failure = assertThrows(
                    Http3ProtocolException.class,
                    () -> reader.readResponseHead(_ -> {
                    }));

            assertThat(failure.errorCode(), is(Http3ErrorCode.FRAME_ERROR));
            assertThat(failure.scope(), is(Http3ProtocolException.Scope.CONNECTION));
        }
    }

    @Test
    void shouldRejectOversizedFieldSectionWithoutMaterializingIt() {
        BufferData frame = BufferData.growing(16);
        VariableLengthEncoder.encode(frame, Http3Protocol.FRAME_HEADERS);
        VariableLengthEncoder.encode(frame, Http3QpackContext.encodedFieldSectionLimit(16) + 1L);
        Http3MessageReader reader = responseReader(Method.GET, 16, frame.readBytes());

        Http3ProtocolException failure = assertThrows(
                Http3ProtocolException.class,
                () -> reader.readResponseHead(_ -> {
                }));

        assertThat(failure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(failure.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    void shouldRejectOversizedTrailersWithoutMaterializingThem() throws Exception {
        BufferData trailers = BufferData.growing(16);
        VariableLengthEncoder.encode(trailers, Http3Protocol.FRAME_HEADERS);
        VariableLengthEncoder.encode(trailers, Http3QpackContext.encodedFieldSectionLimit(64) + 1L);
        Http3MessageReader reader = responseReader(Method.GET,
                                                   64,
                                                   headersFrame(HeaderValues.create(":status", "200")),
                                                   trailers.readBytes());
        reader.readResponseHead(_ -> {
        });

        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                      () -> reader.readEntityDataWithTrailers(8));

        assertThat(failure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(failure.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    void shouldPropagateTransportFailureUnchanged() {
        IllegalStateException transportFailure = new IllegalStateException("transport failure");
        FakeReceiverStream stream = FakeReceiverStream.create();
        stream.failure = transportFailure;
        Http3MessageReader reader = Http3MessageReader.response(stream,
                                                                qpackContext(),
                                                                Http3TestSocketContext.INSTANCE,
                                                                Method.GET,
                                                                16_384,
                                                                NO_OP_FRAME_LISTENER);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                                               () -> reader.readResponseHead(_ -> {
                                               }));

        assertThat(thrown, sameInstance(transportFailure));
    }

    @Test
    void shouldValidateHeaderLimitBeforeRegisteringQpackStream() {
        Http3QpackContext qpackContext = qpackContext();
        FakeReceiverStream stream = FakeReceiverStream.create();

        assertThrows(IllegalArgumentException.class,
                     () -> Http3MessageReader.response(stream,
                                                       qpackContext,
                                                       Http3TestSocketContext.INSTANCE,
                                                       Method.GET,
                                                       -1,
                                                       NO_OP_FRAME_LISTENER));

        Http3MessageReader.response(stream,
                                    qpackContext,
                                    Http3TestSocketContext.INSTANCE,
                                    Method.GET,
                                    16_384,
                                    NO_OP_FRAME_LISTENER)
                .close();
    }

    @Test
    void shouldRejectInvalidResponseStatusFields() {
        List<InvalidHead> invalidHeads = List.of(
                new InvalidHead("malformed status", headersFrame(HeaderValues.create(":status", "20a"))),
                new InvalidHead("missing status", headersFrame(HeaderValues.create("x-field", "value"))),
                new InvalidHead("duplicate status", headersFrame(HeaderValues.create(":status", "200"),
                                                                  HeaderValues.create(":status", "204"))),
                new InvalidHead("switching protocols", headersFrame(HeaderValues.create(":status", "101"))));

        for (InvalidHead invalidHead : invalidHeads) {
            Http3MessageReader reader = responseReader(Method.GET, invalidHead.frame());

            Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                          () -> reader.readResponseHead(_ -> {
                                                          }),
                                                          invalidHead.description());

            assertThat(invalidHead.description(), failure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
            assertThat(invalidHead.description(), failure.scope(), is(Http3ProtocolException.Scope.STREAM));
        }
    }

    @Test
    void shouldAcceptContentMatchingDeclaredLength() throws Exception {
        Http3MessageReader reader = responseReader(Method.GET,
                                                   headersFrame(HeaderValues.create(":status", "200"),
                                                                HeaderValues.create(HeaderNames.CONTENT_LENGTH, "4")),
                                                   Http3Protocol.encodeDataFrame("bo".getBytes(StandardCharsets.UTF_8)),
                                                   Http3Protocol.encodeDataFrame("dy".getBytes(StandardCharsets.UTF_8)));
        reader.readResponseHead(_ -> {
        });

        try (InputStream input = reader.inputStreamWithTrailers()) {
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8), is("body"));
        }
        assertThat(reader.messageComplete(), is(true));
    }

    @Test
    void shouldRejectContentExceedingDeclaredLength() throws Exception {
        Http3MessageReader reader = responseReader(Method.GET,
                                                   headersFrame(HeaderValues.create(":status", "200"),
                                                                HeaderValues.create(HeaderNames.CONTENT_LENGTH, "3")),
                                                   Http3Protocol.encodeDataFrame("body".getBytes(StandardCharsets.UTF_8)));
        reader.readResponseHead(_ -> {
        });

        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                      () -> reader.readEntityDataWithTrailers(8));

        assertThat(failure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
    }

    @Test
    void shouldReportEntityProtocolFailureToStreamOwner() throws Exception {
        Http3MessageReader reader = responseReader(Method.GET,
                                                   headersFrame(HeaderValues.create(":status", "200"),
                                                                HeaderValues.create(HeaderNames.CONTENT_LENGTH, "1")),
                                                   Http3Protocol.encodeDataFrame("body".getBytes(StandardCharsets.UTF_8)));
        AtomicReference<Http3ProtocolException> reported = new AtomicReference<>();
        reader.readResponseHead(_ -> {
        });
        InputStream input = reader.inputStreamWithTrailers(_ -> {
        }, () -> {
        }, reported::set);

        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class, input::read);

        assertThat(reported.get(), is(failure));
    }

    @Test
    void shouldRejectContentShorterThanDeclaredLength() throws Exception {
        Http3MessageReader reader = responseReader(Method.GET,
                                                   headersFrame(HeaderValues.create(":status", "200"),
                                                                HeaderValues.create(HeaderNames.CONTENT_LENGTH, "5")),
                                                   Http3Protocol.encodeDataFrame("body".getBytes(StandardCharsets.UTF_8)));
        reader.readResponseHead(_ -> {
        });

        assertThat(new String(reader.readEntityDataWithTrailers(8), StandardCharsets.UTF_8), is("body"));
        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                      () -> reader.readEntityDataWithTrailers(8));

        assertThat(failure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
    }

    @Test
    void shouldCancelAbandonedContentWithoutValidatingLength() throws Exception {
        FakeReceiverStream stream = FakeReceiverStream.create(
                headersFrame(HeaderValues.create(":status", "200"),
                             HeaderValues.create(HeaderNames.CONTENT_LENGTH, "1")));
        Http3MessageReader reader = Http3MessageReader.response(stream,
                                                                qpackContext(),
                                                                Http3TestSocketContext.INSTANCE,
                                                                Method.GET,
                                                                16_384,
                                                                NO_OP_FRAME_LISTENER);
        reader.readResponseHead(_ -> {
        });

        reader.close();

        assertThat(stream.disconnected, is(true));
    }

    @Test
    void shouldTreatHeadAndBodylessStatusesAsBodyless() throws Exception {
        List<BodylessResponse> responses = List.of(
                new BodylessResponse("HEAD", Method.HEAD, "200",
                                     List.of(HeaderValues.create(HeaderNames.CONTENT_LENGTH, "7"))),
                new BodylessResponse("204", Method.GET, "204", List.of()),
                new BodylessResponse("205", Method.GET, "205",
                                     List.of(HeaderValues.create(HeaderNames.CONTENT_LENGTH, "0"))),
                new BodylessResponse("304", Method.GET, "304",
                                     List.of(HeaderValues.create(HeaderNames.CONTENT_LENGTH, "7"))));

        for (BodylessResponse response : responses) {
            List<Header> headFields = new ArrayList<>();
            headFields.add(HeaderValues.create(":status", response.status()));
            headFields.addAll(response.headers());
            Http3MessageReader completeReader = responseReader(response.method(), headersFrame(headFields));
            completeReader.readResponseHead(_ -> {
            });

            assertThat(response.description(), completeReader.hasEntity(), is(false));
            assertThat(response.description(), completeReader.readEntityDataWithTrailers(8), is(new byte[0]));

            Http3MessageReader dataReader = responseReader(response.method(),
                                                           headersFrame(headFields),
                                                           Http3Protocol.encodeDataFrame(new byte[] {'x'}));
            dataReader.readResponseHead(_ -> {
            });
            Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                          () -> dataReader.readEntityDataWithTrailers(8),
                                                          response.description());

            assertThat(response.description(), failure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));

            if ("204".equals(response.status()) || "304".equals(response.status())) {
                Http3MessageReader trailersReader = responseReader(response.method(),
                                                                   headersFrame(headFields),
                                                                   headersFrame(HeaderValues.create("x-trailer", "value")));
                trailersReader.readResponseHead(_ -> {
                });
                Http3ProtocolException trailersFailure = assertThrows(
                        Http3ProtocolException.class,
                        () -> trailersReader.readEntityDataWithTrailers(8),
                        response.description());
                assertThat(response.description(), trailersFailure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
            }
        }
    }

    @Test
    void shouldAllowTrailersOnResetContentResponse() throws Exception {
        Http3MessageReader reader = responseReader(Method.GET,
                                                   headersFrame(HeaderValues.create(":status", "205"),
                                                                HeaderValues.create(HeaderNames.CONTENT_LENGTH, "0")),
                                                   headersFrame(HeaderValues.create("x-trailer", "done")));
        reader.readResponseHead(_ -> {
        });

        assertThat(reader.hasEntity(), is(false));
        assertThat(reader.readEntityDataWithTrailers(8), is(new byte[0]));
        assertThat(reader.trailers().first(HeaderNames.create("x-trailer")), is(Optional.of("done")));
    }

    @Test
    void shouldAllowTrailersOnHeadResponse() throws Exception {
        Http3MessageReader reader = responseReader(Method.HEAD,
                                                   headersFrame(HeaderValues.create(":status", "200"),
                                                                HeaderValues.create(HeaderNames.CONTENT_LENGTH, "7")),
                                                   headersFrame(HeaderValues.create("x-trailer", "done")));
        reader.readResponseHead(_ -> {
        });

        assertThat(reader.hasEntity(), is(false));
        assertThat(reader.readEntityDataWithTrailers(8), is(new byte[0]));
        assertThat(reader.trailers().first(HeaderNames.create("x-trailer")), is(Optional.of("done")));
    }

    @Test
    void shouldRejectContentLengthForbiddenByResponseStatus() {
        for (String status : List.of("100", "204")) {
            Http3MessageReader reader = responseReader(Method.GET,
                                                       headersFrame(HeaderValues.create(":status", status),
                                                                    HeaderValues.create(HeaderNames.CONTENT_LENGTH, "0")));
            Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                          () -> reader.readResponseHead(_ -> {
                                                          }));
            assertThat(failure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
        }

        Http3MessageReader resetContent = responseReader(
                Method.GET,
                headersFrame(HeaderValues.create(":status", "205"),
                             HeaderValues.create(HeaderNames.CONTENT_LENGTH, "1")));
        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                      () -> resetContent.readResponseHead(_ -> {
                                                      }));
        assertThat(failure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
    }

    @Test
    void shouldExposeValidTrailers() throws Exception {
        Http3MessageReader reader = responseReader(Method.GET,
                                                   headersFrame(HeaderValues.create(":status", "200")),
                                                   Http3Protocol.encodeDataFrame("body".getBytes(StandardCharsets.UTF_8)),
                                                   headersFrame(HeaderValues.create("x-trailer", "done")));
        reader.readResponseHead(_ -> {
        });

        assertThat(new String(reader.readEntityDataWithTrailers(8), StandardCharsets.UTF_8), is("body"));
        assertThat(reader.readEntityDataWithTrailers(8), is(new byte[0]));
        assertThat(reader.trailers().first(HeaderNames.create("x-trailer")), is(Optional.of("done")));
    }

    @Test
    void shouldRejectProhibitedTrailerFields() throws Exception {
        for (Header prohibited : List.of(HeaderValues.create(":path", "/"),
                                         HeaderValues.create("connection", "close"),
                                         HeaderValues.create(HeaderNames.CONTENT_LENGTH, "0"))) {
            Http3MessageReader reader = responseReader(Method.GET,
                                                       headersFrame(HeaderValues.create(":status", "200")),
                                                       headersFrame(prohibited));
            reader.readResponseHead(_ -> {
            });

            Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                          () -> reader.readEntityDataWithTrailers(8));

            assertThat(failure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
            assertThat(failure.scope(), is(Http3ProtocolException.Scope.STREAM));
        }
    }

    @Test
    void shouldRejectDataOrHeadersAfterTrailers() throws Exception {
        List<byte[]> prohibitedFrames = List.of(Http3Protocol.encodeDataFrame(new byte[] {'x'}),
                                                headersFrame(HeaderValues.create("x-second-trailer", "value")));

        for (byte[] prohibitedFrame : prohibitedFrames) {
            Http3MessageReader reader = responseReader(Method.GET,
                                                       headersFrame(HeaderValues.create(":status", "200")),
                                                       headersFrame(HeaderValues.create("x-trailer", "done")),
                                                       prohibitedFrame);
            reader.readResponseHead(_ -> {
            });

            Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                          () -> reader.readEntityDataWithTrailers(8));

            assertThat(failure.errorCode(), is(Http3ErrorCode.FRAME_UNEXPECTED));
            assertThat(failure.scope(), is(Http3ProtocolException.Scope.CONNECTION));
        }
    }

    @Test
    void shouldAllowExtensionFramesAfterTrailers() throws Exception {
        BufferData extension = BufferData.create(3);
        VariableLengthEncoder.encode(extension, 0x21);
        VariableLengthEncoder.encode(extension, 1);
        extension.write((byte) 42);
        Http3MessageReader reader = responseReader(Method.GET,
                                                   headersFrame(HeaderValues.create(":status", "200")),
                                                   headersFrame(HeaderValues.create("x-trailer", "done")),
                                                   extension.readBytes());
        reader.readResponseHead(_ -> {
        });

        assertThat(reader.readEntityDataWithTrailers(8), is(new byte[0]));
        assertThat(reader.trailers().first(HeaderNames.create("x-trailer")), is(Optional.of("done")));
    }

    @Test
    void shouldRejectMalformedPushPromise() throws Exception {
        BufferData pushPromise = BufferData.create(2);
        VariableLengthEncoder.encode(pushPromise, Http3Protocol.FRAME_PUSH_PROMISE);
        VariableLengthEncoder.encode(pushPromise, 0);
        Http3MessageReader reader = responseReader(Method.GET,
                                                   headersFrame(HeaderValues.create(":status", "200")),
                                                   pushPromise.readBytes());
        reader.readResponseHead(_ -> {
        });

        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                      () -> reader.readEntityDataWithTrailers(8));

        assertThat(failure.errorCode(), is(Http3ErrorCode.FRAME_ERROR));
        assertThat(failure.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectMalformedPushPromiseBeforeFinalResponseHeaders() {
        BufferData pushPromise = BufferData.create(2);
        VariableLengthEncoder.encode(pushPromise, Http3Protocol.FRAME_PUSH_PROMISE);
        VariableLengthEncoder.encode(pushPromise, 0);
        Http3MessageReader reader = responseReader(Method.GET, pushPromise.readBytes());

        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                      () -> reader.readResponseHead(_ -> {
                                                      }));

        assertThat(failure.errorCode(), is(Http3ErrorCode.FRAME_ERROR));
        assertThat(failure.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectValidPushPromiseWhenPushWasNotEnabled() throws Exception {
        byte[] fieldSection = QpackCodec.encodeHeaders(List.of(HeaderValues.create(":method", "GET"),
                                                               HeaderValues.create(":scheme", "https"),
                                                               HeaderValues.create(":authority", "example.com"),
                                                               HeaderValues.create(":path", "/pushed")));
        BufferData payload = BufferData.create(fieldSection.length + 1);
        VariableLengthEncoder.encode(payload, 0);
        payload.write(fieldSection);
        BufferData pushPromise = BufferData.growing(payload.available() + 2);
        VariableLengthEncoder.encode(pushPromise, Http3Protocol.FRAME_PUSH_PROMISE);
        VariableLengthEncoder.encode(pushPromise, payload.available());
        pushPromise.write(payload);
        Http3MessageReader reader = responseReader(Method.GET,
                                                   headersFrame(HeaderValues.create(":status", "200")),
                                                   pushPromise.readBytes());
        reader.readResponseHead(_ -> {
        });

        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                      () -> reader.readEntityDataWithTrailers(8));

        assertThat(failure.errorCode(), is(Http3ErrorCode.ID_ERROR));
        assertThat(failure.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectHugePushPromiseAfterReadingOnlyThePushId() throws Exception {
        BufferData pushPromise = BufferData.growing(16);
        VariableLengthEncoder.encode(pushPromise, Http3Protocol.FRAME_PUSH_PROMISE);
        VariableLengthEncoder.encode(pushPromise, 1L << 30);
        VariableLengthEncoder.encode(pushPromise, 0);
        Http3MessageReader reader = responseReader(Method.GET,
                                                   headersFrame(HeaderValues.create(":status", "200")),
                                                   pushPromise.readBytes());
        reader.readResponseHead(_ -> {
        });

        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                      () -> reader.readEntityDataWithTrailers(8));

        assertThat(failure.errorCode(), is(Http3ErrorCode.ID_ERROR));
        assertThat(failure.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldReadSuccessfulConnectDataAndRejectTrailingHeaders() throws Exception {
        Http3MessageReader reader = responseReader(Method.CONNECT,
                                                   headersFrame(HeaderValues.create(":status", "200")),
                                                   Http3Protocol.encodeDataFrame("tunnel".getBytes(StandardCharsets.UTF_8)),
                                                   headersFrame(HeaderValues.create("x-trailer", "not-allowed")));
        reader.readResponseHead(_ -> {
        });

        assertThat(reader.hasEntity(), is(true));
        assertThat(new String(reader.readEntityDataWithTrailers(8), StandardCharsets.UTF_8), is("tunnel"));
        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                      () -> reader.readEntityDataWithTrailers(8));

        assertThat(failure.errorCode(), is(Http3ErrorCode.FRAME_UNEXPECTED));
        assertThat(failure.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldTreatEverySuccessfulConnectAsATunnel() throws Exception {
        Http3MessageReader reader = responseReader(Method.CONNECT,
                                                   headersFrame(HeaderValues.create(":status", "204")),
                                                   Http3Protocol.encodeDataFrame("tunnel".getBytes(StandardCharsets.UTF_8)));
        reader.readResponseHead(_ -> {
        });

        assertThat(reader.hasEntity(), is(true));
        assertThat(new String(reader.readEntityDataWithTrailers(8), StandardCharsets.UTF_8), is("tunnel"));
    }

    @Test
    void shouldIgnoreSuccessfulConnectLengthAndTrailerFields() throws Exception {
        Http3MessageReader reader = responseReader(
                Method.CONNECT,
                headersFrame(HeaderValues.create(":status", "200"),
                             HeaderValues.create(HeaderNames.CONTENT_LENGTH, "invalid"),
                             HeaderValues.create(HeaderNames.CONTENT_LENGTH, "7"),
                             HeaderValues.create(HeaderNames.TRAILER, "x-trailer")),
                Http3Protocol.encodeDataFrame("tunnel".getBytes(StandardCharsets.UTF_8)));

        reader.readResponseHead(_ -> {
        });

        assertThat(reader.contentLength().isEmpty(), is(true));
        assertThat(new String(reader.readEntityDataWithTrailers(8), StandardCharsets.UTF_8), is("tunnel"));
    }

    @Test
    void shouldRejectSuccessfulConnectLengthAndTrailerFieldsForSender() {
        List<Header> prohibited = List.of(
                HeaderValues.create(HeaderNames.CONTENT_LENGTH, "0"),
                HeaderValues.create(HeaderNames.TRAILER, "x-trailer"));

        for (Header header : prohibited) {
            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> Http3MessageReader.validateResponseHeaders(Method.CONNECT,
                                                                      Status.OK_200,
                                                                      WritableHeaders.create()
                                                                              .add(header)));

            assertThat(header.headerName().lowerCase(), failure.getMessage(), containsString("must not contain"));
        }
    }

    @Test
    void shouldRejectConnectionSpecificFields() {
        Http3MessageReader responseReader = responseReader(Method.GET,
                                                           headersFrame(HeaderValues.create(":status", "200"),
                                                                        HeaderValues.create("connection", "close")));

        Http3ProtocolException responseFailure = assertThrows(Http3ProtocolException.class,
                                                              () -> responseReader.readResponseHead(_ -> {
                                                              }));
        assertThat(responseFailure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));

        FakeReceiverStream requestStream = FakeReceiverStream.create(
                headersFrame(HeaderValues.create(":method", "GET"),
                             HeaderValues.create(":scheme", "https"),
                             HeaderValues.create(":authority", "example.com"),
                             HeaderValues.create(":path", "/"),
                             HeaderValues.create("connection", "close")));
        Http3MessageReader requestReader = Http3MessageReader.request(requestStream,
                                                                      qpackContext(),
                                                                      Http3TestSocketContext.INSTANCE,
                                                                      16_384,
                                                                      NO_OP_FRAME_LISTENER);

        Http3ProtocolException requestFailure = assertThrows(Http3ProtocolException.class, requestReader::readRequestHead);
        assertThat(requestFailure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
    }

    @Test
    void shouldAcceptNonEmptyTeListContainingOnlyTrailers() {
        for (String value : List.of("trailers",
                                    "trailers, trailers",
                                    "Trailers,TRAILERS")) {
            WritableHeaders<?> headers = WritableHeaders.create()
                    .add(HeaderValues.create(HeaderNames.TE, value));

            assertThat(value,
                       Http3MessageReader.validateRequestHeaders(headers).isEmpty(),
                       is(true));
        }

        WritableHeaders<?> repeated = WritableHeaders.create()
                .add(HeaderValues.create(HeaderNames.TE, "trailers"))
                .add(HeaderValues.create(HeaderNames.TE, "TRAILERS"));

        assertThat(Http3MessageReader.validateRequestHeaders(repeated).isEmpty(), is(true));
    }

    @Test
    void shouldRejectEmptyOrOtherTeTokens() {
        for (String value : List.of("",
                                    ",",
                                    "trailers,",
                                    ",trailers",
                                    "trailers, gzip",
                                    "gzip, trailers")) {
            WritableHeaders<?> headers = WritableHeaders.create()
                    .add(HeaderValues.create(HeaderNames.TE, value));

            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> Http3MessageReader.validateRequestHeaders(headers),
                    value);

            assertThat(value, failure.getMessage(), containsString("must be trailers"));
        }
    }

    @Test
    void shouldRejectInvalidFieldSyntaxInEveryReceivedSection() {
        for (InvalidField invalid : invalidFields()) {
            Http3MessageReader request = Http3MessageReader.request(
                    FakeReceiverStream.create(headersFrame(HeaderValues.create(":method", "GET"),
                                                           HeaderValues.create(":scheme", "https"),
                                                           HeaderValues.create(":authority", "example.com"),
                                                           HeaderValues.create(":path", "/"),
                                                           invalid.header())),
                    qpackContext(),
                    Http3TestSocketContext.INSTANCE,
                    16_384,
                    NO_OP_FRAME_LISTENER);
            Http3ProtocolException requestFailure = assertThrows(Http3ProtocolException.class,
                                                                  request::readRequestHead,
                                                                  invalid.description());
            assertThat(invalid.description(), requestFailure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
            assertThat(invalid.description(), requestFailure.scope(), is(Http3ProtocolException.Scope.STREAM));

            Http3MessageReader response = responseReader(Method.GET,
                    headersFrame(HeaderValues.create(":status", "200"), invalid.header()));
            Http3ProtocolException responseFailure = assertThrows(Http3ProtocolException.class,
                    () -> response.readResponseHead(_ -> {
                    }), invalid.description());
            assertThat(invalid.description(), responseFailure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
            assertThat(invalid.description(), responseFailure.scope(), is(Http3ProtocolException.Scope.STREAM));

            Http3MessageReader trailers = responseReader(Method.GET,
                    headersFrame(HeaderValues.create(":status", "200")), headersFrame(invalid.header()));
            trailers.readResponseHead(_ -> {
            });
            Http3ProtocolException trailerFailure = assertThrows(Http3ProtocolException.class,
                    () -> trailers.readEntityDataWithTrailers(8), invalid.description());
            assertThat(invalid.description(), trailerFailure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
            assertThat(invalid.description(), trailerFailure.scope(), is(Http3ProtocolException.Scope.STREAM));
        }
    }

    @Test
    void shouldRejectInvalidSchemeBeforeReadingRequestHead() {
        Http3MessageReader request = Http3MessageReader.request(
                FakeReceiverStream.create(headersFrame(HeaderValues.create(":method", "GET"),
                                                       HeaderValues.create(":scheme", "https\n"),
                                                       HeaderValues.create(":authority", "example.com"),
                                                       HeaderValues.create(":path", "/"))),
                qpackContext(),
                Http3TestSocketContext.INSTANCE,
                16_384,
                NO_OP_FRAME_LISTENER);

        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class, request::readRequestHead);

        assertThat(failure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(failure.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    void shouldRejectInvalidPathBeforeReadingRequestHead() {
        Http3MessageReader request = Http3MessageReader.request(
                FakeReceiverStream.create(headersFrame(HeaderValues.create(":method", "GET"),
                                                       HeaderValues.create(":scheme", "https"),
                                                       HeaderValues.create(":authority", "example.com"),
                                                       HeaderValues.create(":path", "/path\n"))),
                qpackContext(),
                Http3TestSocketContext.INSTANCE,
                16_384,
                NO_OP_FRAME_LISTENER);

        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class, request::readRequestHead);

        assertThat(failure.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
        assertThat(failure.scope(), is(Http3ProtocolException.Scope.STREAM));
    }

    @Test
    void shouldRejectInvalidFieldSyntaxBeforeGeneration() {
        for (InvalidField invalid : invalidFields()) {
            WritableHeaders<?> headers = WritableHeaders.create().add(invalid.header());
            assertThrows(IllegalArgumentException.class,
                         () -> Http3MessageReader.validateRequestHeaders(headers), invalid.description());
            assertThrows(IllegalArgumentException.class,
                         () -> Http3MessageReader.validateResponseHeaders(Method.GET, Status.OK_200, headers),
                         invalid.description());
            assertThrows(IllegalArgumentException.class,
                         () -> Http3MessageReader.validateTrailers(headers), invalid.description());
        }
    }

    @Test
    void shouldAcceptValidFieldOctetsAndEmptyValues() {
        for (String value : List.of("", "a\tb", "a b", "caf\u00e9", "\u0080\u00ff")) {
            Http3MessageReader reader = responseReader(Method.GET,
                    headersFrame(HeaderValues.create(":status", "200"), HeaderValues.create("x-field", value)),
                    headersFrame(HeaderValues.create("x-trailer", value)));
            Http3MessageReader.ResponseHead head = reader.readResponseHead(_ -> {
            });
            assertThat(head.headers().get(HeaderNames.create("x-field")).get(), is(value));
            assertThat(reader.readEntityDataWithTrailers(8).length, is(0));
            assertThat(reader.trailers().get(HeaderNames.create("x-trailer")).get(), is(value));
        }
    }

    @Test
    void shouldPreserveSensitivityWhenCombiningResponseAndTrailerFields() {
        Header ordinary = HeaderValues.create("x-private", "ordinary");
        Header sensitive = HeaderValues.create(HeaderNames.create("x-private"), false, true, "secret");
        Http3MessageReader reader = responseReader(Method.GET,
                headersFrame(HeaderValues.create(":status", "200"), ordinary, sensitive),
                headersFrame(ordinary, sensitive));

        Http3MessageReader.ResponseHead head = reader.readResponseHead(_ -> {
        });

        assertThat(head.headers().get(ordinary.headerName()).sensitive(), is(true));
        assertThat(head.headers().get(ordinary.headerName()).allValues(), is(List.of("ordinary", "secret")));
        assertThat(reader.readEntityDataWithTrailers(8).length, is(0));
        assertThat(reader.trailers().get(ordinary.headerName()).sensitive(), is(true));
        assertThat(reader.trailers().get(ordinary.headerName()).allValues(), is(List.of("ordinary", "secret")));
    }

    @Test
    void shouldRejectNegativeResponseTimeoutBeforeReading() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> Http3MessageReader.ResponseOptions.create(Duration.ofNanos(-1), NO_OP_FRAME_LISTENER));

        assertThat(failure.getMessage(), containsString("readTimeout must not be negative"));
    }

    @Test
    void shouldGateRequestResponseAndTrailerCallbacksIndependentlyOfRawBytes() {
        for (boolean enabled : List.of(false, true)) {
            RecordingFrameListener listener = new RecordingFrameListener();
            listener.enabled = enabled;
            Http3MessageReader request = Http3MessageReader.request(
                    FakeReceiverStream.create(headersFrame(HeaderValues.create(":method", "GET"),
                                                           HeaderValues.create(":scheme", "https"),
                                                           HeaderValues.create(":authority", "example.com"),
                                                           HeaderValues.create(":path", "/")),
                                              Http3Protocol.encodeDataFrame(new byte[] {1}),
                                              headersFrame(HeaderValues.create("x-trailer", "request"))),
                    qpackContext(), Http3TestSocketContext.INSTANCE, 16_384, listener);
            request.readRequestHead();
            assertThat(request.readEntityDataWithTrailers(8), is(new byte[] {1}));
            assertThat(request.readEntityDataWithTrailers(8).length, is(0));

            Http3MessageReader response = Http3MessageReader.response(
                    FakeReceiverStream.create(headersFrame(HeaderValues.create(":status", "200")),
                                              headersFrame(HeaderValues.create("x-trailer", "response"))),
                    qpackContext(), Http3TestSocketContext.INSTANCE, Method.GET, 16_384, listener);
            response.readResponseHead(_ -> {
            });
            assertThat(response.readEntityDataWithTrailers(8).length, is(0));

            assertThat(listener.decodedEvents, is(enabled ? List.of("request", "trailers", "response", "trailers") : List.of()));
            assertThat(listener.frameHeaders, is(enabled ? 5 : 0));
            assertThat(listener.framePayloads, is(enabled ? 5 : 0));
            assertThat(listener.rawHeaders, is(5));
            assertThat(listener.rawPayloads, is(5));
        }
    }

    @Test
    void shouldObserveListenerEnablementChangesBetweenFieldSections() {
        RecordingFrameListener listener = new RecordingFrameListener();
        Http3MessageReader response = Http3MessageReader.response(
                FakeReceiverStream.create(headersFrame(HeaderValues.create(":status", "103")),
                                          headersFrame(HeaderValues.create(":status", "200")),
                                          headersFrame(HeaderValues.create("x-trailer", "done"))),
                qpackContext(), Http3TestSocketContext.INSTANCE, Method.GET, 16_384, listener);

        response.readResponseHead(_ -> listener.enabled = true);
        listener.enabled = false;
        assertThat(response.readEntityDataWithTrailers(8).length, is(0));

        assertThat(listener.decodedEvents, is(List.of("response")));
        assertThat(listener.frameHeaders, is(1));
        assertThat(listener.framePayloads, is(1));
        assertThat(listener.rawHeaders, is(3));
        assertThat(listener.rawPayloads, is(3));
    }

    private static List<InvalidField> invalidFields() {
        return List.of(new InvalidField("LF", HeaderValues.create("x-test", "line\nbreak")),
                       new InvalidField("CR", HeaderValues.create("x-test", "line\rbreak")),
                       new InvalidField("NUL", HeaderValues.create("x-test", "a\u0000b")),
                       new InvalidField("CTL", HeaderValues.create("x-test", "a\u0001b")),
                       new InvalidField("DEL", HeaderValues.create("x-test", "a\u007fb")),
                       new InvalidField("later invalid value", HeaderValues.create("x-test", "valid", "line\nbreak")),
                       new InvalidField("space in name", HeaderValues.create("x bad", "value")),
                       new InvalidField("colon in name", HeaderValues.create("x:bad", "value")));
    }

    private static Http3MessageReader responseReader(Method requestMethod,
                                                     byte[]... frames) {
        return responseReader(requestMethod, 16_384, frames);
    }

    private static Http3MessageReader responseReader(Method requestMethod,
                                                     int maxHeadersSize,
                                                     byte[]... frames) {
        return Http3MessageReader.response(FakeReceiverStream.create(frames),
                                           qpackContext(),
                                           Http3TestSocketContext.INSTANCE,
                                           requestMethod,
                                           maxHeadersSize,
                                           NO_OP_FRAME_LISTENER);
    }

    private static Http3QpackContext qpackContext() {
        return Http3QpackContext.create(0, 0, 16_384, _ -> {
        });
    }

    private static byte[] headersFrame(Header... headers) {
        return headersFrame(List.of(headers));
    }

    private static byte[] headersFrame(Iterable<Header> headers) {
        byte[] payload = QpackCodec.encodeHeaders(headers);
        BufferData frame = BufferData.create(VariableLengthEncoder.encodedSize(Http3Protocol.FRAME_HEADERS)
                                                     + VariableLengthEncoder.encodedSize(payload.length)
                                                     + payload.length);
        VariableLengthEncoder.encode(frame, Http3Protocol.FRAME_HEADERS);
        VariableLengthEncoder.encode(frame, payload.length);
        frame.write(payload);
        return frame.readBytes();
    }

    private record InvalidHead(String description, byte[] frame) {
    }

    private record BodylessResponse(String description, Method method, String status, List<Header> headers) {
    }

    private record InvalidField(String description, Header header) {
    }

    private static final class RecordingFrameListener implements Http3FrameListener {
        private final List<String> decodedEvents = new ArrayList<>();
        private boolean enabled;
        private int frameHeaders;
        private int framePayloads;
        private int rawHeaders;
        private int rawPayloads;

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public boolean rawDataEnabled() {
            return true;
        }

        @Override
        public void frameHeader(SocketContext context, long streamId, long frameType, long frameLength, int encodedLength) {
            frameHeaders++;
        }

        @Override
        public void frameData(SocketContext context, long streamId, int byteCount, boolean last) {
            framePayloads++;
        }

        @Override
        public void rawFrameHeader(SocketContext context, long streamId, byte[] data) {
            rawHeaders++;
        }

        @Override
        public void rawFrameData(SocketContext context, long streamId, byte[] data, boolean last) {
            rawPayloads++;
        }

        @Override
        public void requestHeaders(SocketContext context, long streamId, String method, String scheme, String authority,
                                   String path, Headers headers) {
            decodedEvents.add("request");
        }

        @Override
        public void responseHeaders(SocketContext context, long streamId, int status, Headers headers) {
            decodedEvents.add("response");
        }

        @Override
        public void trailers(SocketContext context, long streamId, Headers trailers) {
            decodedEvents.add("trailers");
        }
    }

    private static final class FakeReceiverStream implements QuicReceiverStream {
        private final List<BufferData> buffers;
        private final long dataReceived;
        private FakeStreamReader reader;
        private RuntimeException failure;
        private boolean disconnected;

        private FakeReceiverStream(List<BufferData> buffers) {
            this.buffers = buffers;
            this.dataReceived = buffers.stream()
                    .filter(it -> it != QuicStreamReader.EOF)
                    .mapToLong(BufferData::available)
                    .sum();
        }

        @Override
        public ReceivingStreamState receivingState() {
            return disconnected ? ReceivingStreamState.DATA_READ : ReceivingStreamState.RECV;
        }

        @Override
        public QuicStreamReader connectReader(SequentialScheduler scheduler) {
            if (reader != null && reader.connected()) {
                throw new IllegalStateException("Reader already connected.");
            }
            reader = new FakeStreamReader(this, scheduler, buffers);
            return reader;
        }

        @Override
        public void disconnectReader(QuicStreamReader reader) {
            if (this.reader != reader || this.reader == null || !this.reader.connected()) {
                throw new IllegalStateException("Reader is not connected.");
            }
            disconnected = true;
            this.reader.connected = false;
        }

        @Override
        public void requestStopSending(long errorCode) {
        }

        @Override
        public long dataReceived() {
            return dataReceived;
        }

        @Override
        public long maxStreamData() {
            return dataReceived;
        }

        @Override
        public long rcvErrorCode() {
            return -1;
        }

        @Override
        public long streamId() {
            return 0;
        }

        @Override
        public StreamMode mode() {
            return StreamMode.READ_ONLY;
        }

        @Override
        public boolean isClientInitiated() {
            return true;
        }

        @Override
        public boolean isServerInitiated() {
            return false;
        }

        @Override
        public boolean isBidirectional() {
            return false;
        }

        @Override
        public boolean isLocalInitiated() {
            return false;
        }

        @Override
        public boolean isRemoteInitiated() {
            return true;
        }

        @Override
        public int type() {
            return 0x02;
        }

        @Override
        public QuicStream.StreamState state() {
            return receivingState();
        }

        private static FakeReceiverStream create(byte[]... frames) {
            List<BufferData> buffers = new ArrayList<>(frames.length + 1);
            for (byte[] frame : frames) {
                buffers.add(BufferData.create(frame));
            }
            buffers.add(QuicStreamReader.EOF);
            return new FakeReceiverStream(buffers);
        }
    }

    private static final class FakeStreamReader extends QuicStreamReader {
        private final FakeReceiverStream stream;
        private final SequentialScheduler scheduler;
        private final List<BufferData> buffers;
        private int index;
        private boolean connected = true;
        private boolean started;

        private FakeStreamReader(FakeReceiverStream stream,
                                 SequentialScheduler scheduler,
                                 List<BufferData> buffers) {
            super(scheduler);
            this.stream = stream;
            this.scheduler = scheduler;
            this.buffers = buffers;
        }

        @Override
        public QuicReceiverStream.ReceivingStreamState receivingState() {
            return stream.receivingState();
        }

        @Override
        public Optional<BufferData> poll() {
            ensureConnected();
            if (stream.failure != null) {
                throw stream.failure;
            }
            if (!started || index >= buffers.size()) {
                return Optional.empty();
            }
            return Optional.of(buffers.get(index++));
        }

        @Override
        public Optional<BufferData> peek() {
            ensureConnected();
            if (!started || index >= buffers.size()) {
                return Optional.empty();
            }
            return Optional.of(buffers.get(index));
        }

        @Override
        public Optional<QuicReceiverStream> stream() {
            return connected ? Optional.of(stream) : Optional.empty();
        }

        @Override
        public boolean connected() {
            return connected;
        }

        @Override
        public boolean started() {
            return started;
        }

        @Override
        public void start() {
            started = true;
            scheduler.runOrSchedule();
        }

        private void ensureConnected() {
            if (!connected) {
                throw new IllegalStateException("Reader not connected.");
            }
        }
    }
}
