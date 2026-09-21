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

package io.helidon.webclient.http3;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Size;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Context;
import io.helidon.common.socket.SocketContext;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3FrameListener;
import io.helidon.http.http3.Http3MessageReader;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3QpackContext;
import io.helidon.http.http3.Http3StreamSupport;
import io.helidon.http.http3.Http3StreamType;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicVersion;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStreamReader;
import io.helidon.quic.stream.QuicStreamWriter;
import io.helidon.webclient.api.ResolvedClientTarget;

import org.junit.jupiter.api.Test;

import static io.helidon.quic.stream.QuicReceiverStream.ReceivingStreamState.RECV;
import static io.helidon.quic.stream.QuicSenderStream.SendingStreamState.DATA_RECVD;
import static io.helidon.quic.stream.QuicSenderStream.SendingStreamState.READY;
import static io.helidon.quic.stream.QuicSenderStream.SendingStreamState.RESET_RECVD;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http3ExchangeClientTest {
    @Test
    void shouldExposeIndependentConnectionTimeouts() {
        Duration initialResponseTimeout = Duration.ofSeconds(3);
        Duration handshakeTimeout = Duration.ofSeconds(5);
        Duration streamOpenTimeout = Duration.ofSeconds(7);
        Http3ClientProtocolConfig config = Http3ClientProtocolConfig.builder()
                .initialResponseTimeout(initialResponseTimeout)
                .handshakeTimeout(handshakeTimeout)
                .streamOpenTimeout(streamOpenTimeout)
                .buildPrototype();

        assertThat(config.initialResponseTimeout(), equalTo(initialResponseTimeout));
        assertThat(config.handshakeTimeout(), equalTo(handshakeTimeout));
        assertThat(config.streamOpenTimeout(), equalTo(streamOpenTimeout));
        Http3ClientProtocolConfig defaults = Http3ClientProtocolConfig.create();
        assertThat(defaults.initialResponseTimeout(), equalTo(Duration.ofSeconds(10)));
        assertThat(defaults.handshakeTimeout(), equalTo(Duration.ofSeconds(10)));
        assertThat(defaults.streamOpenTimeout(), equalTo(Duration.ofSeconds(10)));
        assertThat(Http3ClientProtocolConfig.builder()
                           .handshakeTimeout(Duration.ofSeconds(13))
                           .buildPrototype()
                           .streamOpenTimeout(),
                   equalTo(Duration.ofSeconds(10)));

        Config externalConfig = Config.just(ConfigSources.create(Map.of("initial-response-timeout", "PT5S",
                                                                        "handshake-timeout", "PT13S",
                                                                        "stream-open-timeout", "PT9S")));
        Http3ClientProtocolConfig external = Http3ClientProtocolConfig.create(externalConfig);
        assertThat(external.initialResponseTimeout(), equalTo(Duration.ofSeconds(5)));
        assertThat(external.handshakeTimeout(), equalTo(Duration.ofSeconds(13)));
        assertThat(external.streamOpenTimeout(), equalTo(Duration.ofSeconds(9)));
    }

    @Test
    void shouldInheritCommonHttpConfiguration() {
        Http3ClientProtocolConfig config = Http3ClientProtocolConfig.builder()
                .maxHeadersSize(12_345)
                .maxBufferedEntitySize(Size.create(8, Size.Unit.KIB))
                .validateRequestHeaders(false)
                .validateResponseHeaders(false)
                .sendErrorDetails(true)
                .buildPrototype();

        assertThat(config.maxHeadersSize(), equalTo(12_345));
        assertThat(config.maxBufferedEntitySize().toBytes(), equalTo(8_192L));
        assertThat(config.validateRequestHeaders(), equalTo(false));
        assertThat(config.validateResponseHeaders(), equalTo(false));
        assertThat(config.sendErrorDetails(), equalTo(true));
        assertThat(Http3ClientProtocolConfig.create().sendErrorDetails(), equalTo(false));
    }

    @Test
    void shouldExposeHttp3ApplicationErrorsForQuicRuntime() {
        assertThat(Http3ExchangeClient.applicationErrors().apply(Http3ErrorCode.REQUEST_REJECTED.code()),
                   equalTo("H3_REQUEST_REJECTED"));
    }

    @Test
    void shouldMapConfiguredClientSettings() {
        QuicConfig quicConfig = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V2))
                .idleTimeout(Duration.ofSeconds(42))
                .maxUdpPayloadSize(1_350)
                .maxBidiStreams(7)
                .buildPrototype();
        Http3ClientProtocolConfig protocolConfig = Http3ClientProtocolConfig.builder()
                .maxHeadersSize(12_345)
                .maxFieldSectionSize(32_768)
                .qpackMaxTableCapacity(4_096)
                .qpackBlockedStreams(16)
                .initialResponseTimeout(Duration.ofSeconds(13))
                .handshakeTimeout(Duration.ofSeconds(15))
                .log(it -> it.receiveLog(false)
                        .loggerName("http3.test"))
                .quic(quicConfig)
                .buildPrototype();

        Http3ExchangeClient.ClientSettings settings = Http3ExchangeClient.clientSettings(protocolConfig);

        assertThat(settings.idleTimeoutMillis(), equalTo(Duration.ofSeconds(42).toMillis()));
        assertThat(settings.maxHeadersSize(), equalTo(12_345));
        assertThat(settings.initialResponseTimeout(), equalTo(Duration.ofSeconds(13)));
        assertThat(settings.localSettings().maxFieldSectionSize().orElseThrow(), equalTo(32_768L));
        assertThat(settings.localSettings().qpackMaxTableCapacity(), equalTo(4_096L));
        assertThat(settings.localSettings().qpackBlockedStreams(), equalTo(16L));
        assertThat(settings.quicConfig(), sameInstance(quicConfig));
        assertThat(settings.quicConfig().availableVersions(), equalTo(List.of(QuicVersion.QUIC_V2)));
        assertThat(settings.quicConfig().idleTimeout().toMillis(), equalTo(Duration.ofSeconds(42).toMillis()));
        assertThat(settings.quicConfig().maxUdpPayloadSize(), equalTo(1_350));
        assertThat(settings.quicConfig().maxBidiStreams(), equalTo(7L));
        assertThat(settings.logConfig().receiveLog(), equalTo(false));
        assertThat(settings.logConfig().sendLog(), equalTo(true));
        assertThat(settings.logConfig().loggerName().orElseThrow(), equalTo("http3.test"));
        assertThat(controlStreamSettings(Http3Protocol.controlStreamPreamble(settings.localSettings())),
                   equalTo(Map.of(0x01L, 4_096L, 0x06L, 32_768L, 0x07L, 16L)));
    }

    @Test
    void shouldUseDefaultClientSettings() {
        Http3ExchangeClient.ClientSettings settings = Http3ExchangeClient.clientSettings(Http3ClientProtocolConfig.create());

        assertThat(settings.idleTimeoutMillis(), equalTo(Duration.ofSeconds(30).toMillis()));
        assertThat(settings.maxHeadersSize(), equalTo(16_384));
        assertThat(settings.initialResponseTimeout(), equalTo(Duration.ofSeconds(10)));
        assertThat(settings.localSettings().maxFieldSectionSize().isEmpty(), equalTo(true));
        assertThat(settings.localSettings().qpackMaxTableCapacity(), equalTo(0L));
        assertThat(settings.localSettings().qpackBlockedStreams(), equalTo(0L));
        assertThat(settings.quicConfig().availableVersions(), equalTo(List.of(QuicVersion.QUIC_V2, QuicVersion.QUIC_V1)));
        assertThat(settings.quicConfig().idleTimeout().toMillis(), equalTo(Duration.ofSeconds(30).toMillis()));
        assertThat(settings.quicConfig().maxBidiStreams(), equalTo(100L));
        assertThat(settings.logConfig().receiveLog(), equalTo(true));
        assertThat(settings.logConfig().sendLog(), equalTo(true));
        assertThat(controlStreamSettings(Http3Protocol.controlStreamPreamble(settings.localSettings())),
                   equalTo(Map.of(0x01L, 0L, 0x07L, 0L)));
    }

    @Test
    void shouldRequireThreePeerUnidirectionalStreams() {
        long minimum = Http3Protocol.MINIMUM_PEER_UNI_STREAMS;
        long insufficient = minimum - 1;
        QuicConfig insufficientQuic = QuicConfig.builder()
                .maxUniStreams(insufficient)
                .buildPrototype();
        String expectedMessage = "HTTP/3 requires quic.maxUniStreams to be at least "
                + minimum + ": " + insufficient;

        IllegalArgumentException programmaticFailure = assertThrows(
                IllegalArgumentException.class,
                () -> Http3ClientProtocolConfig.builder().quic(insufficientQuic).buildPrototype());
        assertThat(programmaticFailure.getMessage(), equalTo(expectedMessage));

        QuicConfig minimumQuic = QuicConfig.builder()
                .maxUniStreams(minimum)
                .buildPrototype();
        Http3ClientProtocolConfig minimumConfig = Http3ClientProtocolConfig.builder()
                .quic(minimumQuic)
                .buildPrototype();
        assertThat(Http3ExchangeClient.clientSettings(minimumConfig).quicConfig(), sameInstance(minimumQuic));

        Config externalConfig = Config.just(
                ConfigSources.create(Map.of("quic.max-uni-streams", Long.toString(insufficient))));
        IllegalArgumentException externalFailure = assertThrows(
                IllegalArgumentException.class,
                () -> Http3ClientProtocolConfig.create(externalConfig));
        assertThat(externalFailure.getMessage(), equalTo(expectedMessage));

        Http3ClientProtocolConfig runtimeInvalidConfig =
                mock(Http3ClientProtocolConfig.class, delegatesTo(Http3ClientProtocolConfig.create()));
        when(runtimeInvalidConfig.quic()).thenReturn(Optional.of(insufficientQuic));
        IllegalArgumentException runtimeFailure = assertThrows(
                IllegalArgumentException.class,
                () -> Http3ExchangeClient.clientSettings(runtimeInvalidConfig));
        assertThat(runtimeFailure.getMessage(), equalTo(expectedMessage));
    }

    @Test
    void shouldAcceptClientSettingBoundaries() {
        long maxVarInt = VariableLengthEncoder.MAX_ENCODED_INTEGER;

        Http3ClientProtocolConfig sentinel = Http3ClientProtocolConfig.builder()
                .maxFieldSectionSize(-1)
                .qpackMaxTableCapacity(-1)
                .qpackBlockedStreams(-1)
                .buildPrototype();
        assertThat(sentinel.maxFieldSectionSize(), equalTo(-1L));
        assertThat(sentinel.qpackMaxTableCapacity(), equalTo(-1L));
        assertThat(sentinel.qpackBlockedStreams(), equalTo(-1L));

        Http3ClientProtocolConfig zero = Http3ClientProtocolConfig.builder()
                .maxFieldSectionSize(0)
                .qpackMaxTableCapacity(0)
                .qpackBlockedStreams(0)
                .buildPrototype();
        assertThat(zero.maxFieldSectionSize(), equalTo(0L));
        assertThat(zero.qpackMaxTableCapacity(), equalTo(0L));
        assertThat(zero.qpackBlockedStreams(), equalTo(0L));

        Http3ClientProtocolConfig maximum = Http3ClientProtocolConfig.builder()
                .maxFieldSectionSize(maxVarInt)
                .qpackMaxTableCapacity(maxVarInt)
                .qpackBlockedStreams(maxVarInt)
                .buildPrototype();
        assertThat(maximum.maxFieldSectionSize(), equalTo(maxVarInt));
        assertThat(maximum.qpackMaxTableCapacity(), equalTo(maxVarInt));
        assertThat(maximum.qpackBlockedStreams(), equalTo(maxVarInt));

        Config externalConfig = Config.just(
                ConfigSources.create(Map.of("max-field-section-size", Long.toString(maxVarInt),
                                            "qpack-max-table-capacity", Long.toString(maxVarInt),
                                            "qpack-blocked-streams", Long.toString(maxVarInt))));
        Http3ClientProtocolConfig external = Http3ClientProtocolConfig.create(externalConfig);
        assertThat(external.maxFieldSectionSize(), equalTo(maxVarInt));
        assertThat(external.qpackMaxTableCapacity(), equalTo(maxVarInt));
        assertThat(external.qpackBlockedStreams(), equalTo(maxVarInt));
    }

    @Test
    void shouldRethrowDispatchFailureSwallowedByRequestProducer() {
        IllegalStateException dispatchFailure = new IllegalStateException("test request dispatch failure");
        AtomicReference<RuntimeException> swallowedFailure = new AtomicReference<>();
        Http3RequestBody requestBody = Http3RequestBody.create(outputStream -> {
            try (outputStream) {
                byte[] chunk = new byte[16 * 1024];
                for (int i = 0; i < 4; i++) {
                    try {
                        outputStream.write(chunk);
                    } catch (RuntimeException e) {
                        swallowedFailure.set(e);
                        break;
                    }
                }
            }
        }, -1);
        RequestExecutionFixture fixture = requestExecutionFixture(requestBody);
        when(fixture.writer().scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.failedFuture(dispatchFailure));

        Throwable requestFailure = fixture.execute();

        assertThat(swallowedFailure.get(), sameInstance(dispatchFailure));
        assertThat(requestFailure.getCause(), sameInstance(dispatchFailure));
    }

    @Test
    void shouldPreserveDispatchFailureFromBufferedRequestClose() {
        IllegalStateException dispatchFailure = new IllegalStateException("test buffered close failure");
        AtomicReference<RuntimeException> producerFailure = new AtomicReference<>();
        Http3RequestBody requestBody = Http3RequestBody.create(outputStream -> {
            try {
                try (outputStream) {
                    outputStream.write(1);
                }
            } catch (RuntimeException e) {
                producerFailure.set(e);
                throw e;
            }
        }, -1);
        RequestExecutionFixture fixture = requestExecutionFixture(requestBody);
        when(fixture.writer().scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean()))
                .thenReturn(CompletableFuture.failedFuture(dispatchFailure));

        Throwable requestFailure = fixture.execute();

        assertThat(producerFailure.get(), sameInstance(dispatchFailure));
        assertThat(requestFailure.getCause(), sameInstance(dispatchFailure));
    }

    @Test
    void shouldRejectUnknownLengthRequestUnderrunAgainstFinalHeaders() {
        CapturedRequest captured = captureRequest(streamingRequestBody(5, -1), 6);

        assertThat("An underrun must not complete the request with FIN", captured.finCount(), equalTo(0));
        assertThat("The producer must report the final Content-Length mismatch", captured.failure(), notNullValue());
        assertThat(captured.headers().get(HeaderNames.CONTENT_LENGTH).get(), equalTo("6"));
        assertThat(captured.dataLength(), equalTo(5L));
    }

    @Test
    void shouldRejectUnknownLengthRequestOverrunAgainstFinalHeaders() {
        CapturedRequest captured = captureRequest(streamingRequestBody(5, -1), 4);

        assertThat("An overrun must not complete the request with FIN", captured.finCount(), equalTo(0));
        assertThat("The producer must report the final Content-Length mismatch", captured.failure(), notNullValue());
        assertThat(captured.headers().get(HeaderNames.CONTENT_LENGTH).get(), equalTo("4"));
        assertThat("The oversized write must not be dispatched", captured.dataLength(), equalTo(0L));
    }

    @Test
    void shouldRejectEmptyStreamingRequestWithPositiveFinalContentLength() {
        CapturedRequest captured = captureRequest(streamingRequestBody(0, -1), 1);

        assertThat("An empty producer must not dispatch HEADERS declaring a nonempty body",
                   captured.headers(),
                   nullValue());
        assertThat(captured.dataLength(), equalTo(0L));
        assertThat(captured.finCount(), equalTo(0));
        assertThat("The producer must report the final Content-Length mismatch", captured.failure(), notNullValue());
    }

    @Test
    void shouldUseFinalContentLengthForStreamingRequest() {
        CapturedRequest captured = captureRequest(streamingRequestBody(5, 6), 5);

        assertThat("The final headers, not the original body declaration, govern framing",
                   captured.failure(),
                   nullValue());
        assertThat(captured.headers().get(HeaderNames.CONTENT_LENGTH).get(), equalTo("5"));
        assertThat(captured.dataLength(), equalTo(5L));
        assertThat(captured.finCount(), equalTo(1));
    }

    @Test
    void shouldAllowUnknownLengthStreamingRequestWithoutContentLength() {
        CapturedRequest captured = captureRequest(streamingRequestBody(5, -1), -1);

        assertThat(captured.failure(), nullValue());
        assertThat(captured.headers().contains(HeaderNames.CONTENT_LENGTH), equalTo(false));
        assertThat(captured.dataLength(), equalTo(5L));
        assertThat(captured.finCount(), equalTo(1));
    }

    @Test
    void shouldNotCompleteKnownBodyWithMismatchedContentLength() {
        CapturedRequest captured = captureRequest(Http3RequestBody.create(new byte[5]), 6);

        if (captured.finCount() == 0) {
            assertThat("The transport must report rejection of inconsistent finalized headers",
                       captured.failure(),
                       notNullValue());
        } else {
            assertThat(captured.failure(), nullValue());
            assertThat(captured.finCount(), equalTo(1));
            long declaredLength = Long.parseLong(captured.headers().get(HeaderNames.CONTENT_LENGTH).get());
            assertThat("A completed request must match its encoded Content-Length",
                       captured.dataLength(),
                       equalTo(declaredLength));
        }
    }

    @Test
    void shouldFinishRequestStreamExactlyOnce() {
        RequestStreamFixture fixture = requestStreamFixture();

        fixture.requestStream().finish(StreamOutcome.COMPLETED);
        fixture.requestStream().finish(StreamOutcome.COMPLETED);

        assertThat(fixture.completions().get(), equalTo(1));
        verify(fixture.stream(), times(1)).disconnectReader(fixture.reader());
    }

    @Test
    void shouldFailPendingResponseTrailersWhenSessionCloses() {
        RequestStreamFixture fixture = requestStreamFixture();
        IllegalStateException closeFailure = new IllegalStateException("test session failure");

        Http3ExchangeClient.ConnectionSession.completeResponseTrailers(
                List.of(fixture.requestStream()), closeFailure);

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> fixture.requestStream().responseTrailers().join());
        assertThat(failure.getCause(), sameInstance(closeFailure));
    }

    @Test
    void shouldFailResponseTrailersWithCanonicalStreamFailure() {
        RequestStreamFixture fixture = requestStreamFixture();
        IllegalStateException closeFailure = new IllegalStateException("test session failure");
        when(fixture.session().rejectsStream(fixture.stream().streamId())).thenReturn(true);

        fixture.requestStream().sessionClosed(closeFailure, StreamOutcome.ERROR);
        Http3ExchangeClient.ConnectionSession.completeResponseTrailers(
                List.of(fixture.requestStream()), closeFailure);

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> fixture.requestStream().responseTrailers().join());
        assertThat(failure.getCause(), instanceOf(Http3RequestFailureSupport.ConnectionRetiredException.class));
        assertThat(failure.getCause().getCause(), sameInstance(closeFailure));
    }

    @Test
    void shouldFailSiblingResponseTrailersIndependently() throws Exception {
        RequestStreamFixture blockingFixture = requestStreamFixture();
        RequestStreamFixture siblingFixture = requestStreamFixture();
        CountDownLatch blockingCallbackEntered = new CountDownLatch(1);
        CountDownLatch releaseBlockingCallback = new CountDownLatch(1);
        CountDownLatch siblingCallbackCompleted = new CountDownLatch(1);
        blockingFixture.requestStream().responseTrailers().whenComplete((_, _) -> {
            blockingCallbackEntered.countDown();
            try {
                releaseBlockingCallback.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        siblingFixture.requestStream()
                .responseTrailers()
                .whenComplete((_, _) -> siblingCallbackCompleted.countDown());

        try {
            Http3ExchangeClient.ConnectionSession.completeResponseTrailers(
                    List.of(blockingFixture.requestStream(), siblingFixture.requestStream()),
                    new IllegalStateException("test session failure"));

            assertThat(blockingCallbackEntered.await(10, TimeUnit.SECONDS), equalTo(true));
            assertThat(siblingCallbackCompleted.await(10, TimeUnit.SECONDS), equalTo(true));
            assertThat(blockingFixture.requestStream().responseTrailers().isCompletedExceptionally(), equalTo(true));
            assertThat(siblingFixture.requestStream().responseTrailers().isCompletedExceptionally(), equalTo(true));
        } finally {
            releaseBlockingCallback.countDown();
        }
    }

    @Test
    void shouldPreserveCompletedResponseTrailersWhenSessionCloses() {
        RequestStreamFixture fixture = requestStreamFixture();
        Headers completedTrailers = WritableHeaders.create();
        fixture.requestStream().responseTrailers().complete(completedTrailers);

        Http3ExchangeClient.ConnectionSession.completeResponseTrailers(
                List.of(fixture.requestStream()), new IllegalStateException("test session failure"));

        assertThat(fixture.requestStream().responseTrailers().join(), sameInstance(completedTrailers));
    }

    @Test
    void shouldFinishRequestStreamOnlyAfterSendingStops() {
        CompletableFuture<QuicSenderStream.SendingStreamState> sendCompletion = new CompletableFuture<>();
        RequestStreamFixture fixture = requestStreamFixture(sendCompletion);

        fixture.requestStream().finish(StreamOutcome.COMPLETED);

        assertThat(fixture.completions().get(), equalTo(0));

        sendCompletion.complete(DATA_RECVD);

        assertThat(fixture.completions().get(), equalTo(1));
    }

    @Test
    void shouldReportLateSendingFailureAsStreamError() {
        CompletableFuture<QuicSenderStream.SendingStreamState> sendCompletion = new CompletableFuture<>();
        RequestStreamFixture fixture = requestStreamFixture(sendCompletion);

        fixture.requestStream().finish(StreamOutcome.COMPLETED);
        sendCompletion.completeExceptionally(new IllegalStateException("test send failure"));

        verify(fixture.streamObservation()).close(StreamOutcome.ERROR);
        assertThat(fixture.completions().get(), equalTo(1));
    }

    @Test
    void shouldReportLateSendingReset() {
        CompletableFuture<QuicSenderStream.SendingStreamState> sendCompletion = new CompletableFuture<>();
        RequestStreamFixture fixture = requestStreamFixture(sendCompletion);
        when(fixture.stream().sndErrorCode()).thenReturn(Http3ErrorCode.INTERNAL_ERROR.code());

        fixture.requestStream().finish(StreamOutcome.COMPLETED);
        sendCompletion.complete(RESET_RECVD);

        verify(fixture.streamObservation()).close(StreamOutcome.RESET);
        assertThat(fixture.completions().get(), equalTo(1));
    }

    @Test
    void shouldPreserveCompletionWhenPeerStopsUnusedRequestBody() {
        for (Http3ErrorCode errorCode : List.of(Http3ErrorCode.NO_ERROR, Http3ErrorCode.REQUEST_CANCELLED)) {
            CompletableFuture<QuicSenderStream.SendingStreamState> sendCompletion = new CompletableFuture<>();
            RequestStreamFixture fixture = requestStreamFixture(sendCompletion);
            when(fixture.stream().stopSendingReceived()).thenReturn(true);
            when(fixture.stream().sndErrorCode()).thenReturn(errorCode.code());

            fixture.requestStream().finish(StreamOutcome.COMPLETED);
            sendCompletion.complete(RESET_RECVD);

            verify(fixture.streamObservation()).close(StreamOutcome.COMPLETED);
            assertThat(fixture.completions().get(), equalTo(1));
        }
    }

    @Test
    void shouldPreserveCompletionWhenLocalRequestBodyCancellationCompletes() {
        CompletableFuture<QuicSenderStream.SendingStreamState> sendCompletion = new CompletableFuture<>();
        RequestStreamFixture fixture = requestStreamFixture(sendCompletion);
        when(fixture.stream().sndErrorCode()).thenReturn(Http3ErrorCode.REQUEST_CANCELLED.code());

        fixture.requestStream().finish(StreamOutcome.COMPLETED);
        sendCompletion.complete(RESET_RECVD);

        verify(fixture.streamObservation()).close(StreamOutcome.COMPLETED);
        assertThat(fixture.completions().get(), equalTo(1));
    }

    @Test
    void shouldPreserveCancellationWhenSendingResetCompletes() {
        CompletableFuture<QuicSenderStream.SendingStreamState> sendCompletion = new CompletableFuture<>();
        RequestStreamFixture fixture = requestStreamFixture(sendCompletion);

        fixture.requestStream().cancel();
        sendCompletion.complete(RESET_RECVD);

        verify(fixture.streamObservation()).close(StreamOutcome.CANCELLED);
        assertThat(fixture.completions().get(), equalTo(1));
    }

    @Test
    void shouldFinishRequestStreamWhenReaderCleanupFails() {
        RequestStreamFixture fixture = requestStreamFixture();
        doAnswer(_ -> {
            throw new IllegalArgumentException("reader cleanup failed");
        }).when(fixture.stream()).disconnectReader(fixture.reader());

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.requestStream().finish(StreamOutcome.COMPLETED));

        assertThat(failure.getMessage(), equalTo("reader cleanup failed"));
        assertThat(fixture.completions().get(), equalTo(1));
    }

    @Test
    void shouldCancelEachRequestStreamDirectionExactlyOnce() {
        RequestStreamFixture fixture = requestStreamFixture();

        fixture.requestStream().cancelRequestBody();
        fixture.requestStream().cancelRequestBody();
        fixture.requestStream().cancelResponseBody();
        fixture.requestStream().cancelResponseBody();

        verify(fixture.writer(), times(1)).reset(Http3ErrorCode.REQUEST_CANCELLED.code());
        verify(fixture.stream(), times(1)).requestStopSending(Http3ErrorCode.REQUEST_CANCELLED.code());
        assertThat(fixture.responseCancellations().get(), equalTo(1));
        fixture.requestStream().finish(StreamOutcome.COMPLETED);
    }

    @Test
    void shouldCancelBothDirectionsWhenIncompleteResponseIsClosed() {
        RequestStreamFixture fixture = requestStreamFixture();
        CompletableFuture<Headers> trailers = new CompletableFuture<>();
        Http3StreamedResponse response = new Http3StreamedResponse(mock(Http3MessageReader.ResponseHead.class),
                                                                   mock(ResolvedClientTarget.class),
                                                                   Instant.EPOCH,
                                                                   trailers,
                                                                   null,
                                                                   fixture.requestStream(),
                                                                   true);

        response.closeResource();

        verify(fixture.writer()).reset(Http3ErrorCode.REQUEST_CANCELLED.code());
        verify(fixture.stream()).requestStopSending(Http3ErrorCode.REQUEST_CANCELLED.code());
        verify(fixture.streamObservation()).close(StreamOutcome.CANCELLED);
        assertThat(trailers.isCompletedExceptionally(), equalTo(true));
    }

    @Test
    void shouldReportResponseReadTimeoutAsStreamError() {
        RequestStreamFixture fixture = requestStreamFixture();
        CompletableFuture<Headers> trailers = new CompletableFuture<>();
        Http3StreamedResponse response = new Http3StreamedResponse(mock(Http3MessageReader.ResponseHead.class),
                                                                   mock(ResolvedClientTarget.class),
                                                                   Instant.EPOCH,
                                                                   trailers,
                                                                   null,
                                                                   fixture.requestStream(),
                                                                   true);

        response.readTimedOut(new IllegalStateException("test timeout"));

        verify(fixture.writer()).reset(Http3ErrorCode.REQUEST_CANCELLED.code());
        verify(fixture.stream()).requestStopSending(Http3ErrorCode.REQUEST_CANCELLED.code());
        verify(fixture.streamObservation()).close(StreamOutcome.ERROR);
        assertThat(trailers.isCompletedExceptionally(), equalTo(true));
    }

    @Test
    void shouldPreserveIncompleteResponseCleanupFailures() {
        RequestStreamFixture fixture = requestStreamFixture();
        IllegalStateException requestFailure = new IllegalStateException("request cancellation failed");
        IllegalArgumentException responseFailure = new IllegalArgumentException("response cancellation failed");
        UnsupportedOperationException finishFailure = new UnsupportedOperationException("finish failed");
        doAnswer(_ -> {
            throw requestFailure;
        }).when(fixture.writer()).reset(Http3ErrorCode.REQUEST_CANCELLED.code());
        doAnswer(_ -> {
            throw responseFailure;
        }).when(fixture.stream()).requestStopSending(Http3ErrorCode.REQUEST_CANCELLED.code());
        doAnswer(_ -> {
            throw finishFailure;
        }).when(fixture.stream()).disconnectReader(fixture.reader());
        Http3StreamedResponse response = new Http3StreamedResponse(mock(Http3MessageReader.ResponseHead.class),
                                                                   mock(ResolvedClientTarget.class),
                                                                   Instant.EPOCH,
                                                                   new CompletableFuture<>(),
                                                                   null,
                                                                   fixture.requestStream(),
                                                                   true);

        RuntimeException failure = assertThrows(RuntimeException.class, response::closeResource);

        assertThat(failure, sameInstance(requestFailure));
        assertThat(failure.getSuppressed().length, equalTo(2));
        assertThat(failure.getSuppressed()[0], sameInstance(responseFailure));
        assertThat(failure.getSuppressed()[1], sameInstance(finishFailure));
    }

    @Test
    void shouldRejectInvalidClientSettings() {
        long maxVarInt = VariableLengthEncoder.MAX_ENCODED_INTEGER;
        Http3ClientProtocolConfig timeoutBoundary = Http3ClientProtocolConfig.builder()
                .initialResponseTimeout(Duration.ofNanos(1))
                .handshakeTimeout(Duration.ofNanos(Long.MAX_VALUE))
                .streamOpenTimeout(Duration.ofNanos(Long.MAX_VALUE))
                .buildPrototype();
        assertThat(timeoutBoundary.initialResponseTimeout(), equalTo(Duration.ofNanos(1)));
        assertThat(timeoutBoundary.handshakeTimeout(), equalTo(Duration.ofNanos(Long.MAX_VALUE)));
        assertThat(timeoutBoundary.streamOpenTimeout(), equalTo(Duration.ofNanos(Long.MAX_VALUE)));

        IllegalArgumentException invalidQpack = assertThrows(IllegalArgumentException.class,
                                                             () -> Http3ClientProtocolConfig.builder()
                                                                     .qpackBlockedStreams(-2)
                                                                     .buildPrototype());
        assertThat(invalidQpack.getMessage(),
                   equalTo("qpackBlockedStreams must be -1 or a QUIC variable-length integer: -2"));
        long tooLarge = maxVarInt + 1;
        assertThrows(IllegalArgumentException.class,
                     () -> Http3ClientProtocolConfig.builder().maxFieldSectionSize(-2).buildPrototype());
        assertThrows(IllegalArgumentException.class,
                     () -> Http3ClientProtocolConfig.builder().qpackMaxTableCapacity(-2).buildPrototype());
        assertThrows(IllegalArgumentException.class,
                     () -> Http3ClientProtocolConfig.builder().maxFieldSectionSize(tooLarge).buildPrototype());
        assertThrows(IllegalArgumentException.class,
                     () -> Http3ClientProtocolConfig.builder().qpackMaxTableCapacity(tooLarge).buildPrototype());
        assertThrows(IllegalArgumentException.class,
                     () -> Http3ClientProtocolConfig.builder().qpackBlockedStreams(tooLarge).buildPrototype());

        IllegalArgumentException invalidTimeout = assertThrows(IllegalArgumentException.class,
                                                               () -> Http3ClientProtocolConfig.builder()
                                                                       .initialResponseTimeout(Duration.ZERO)
                                                                       .buildPrototype());
        assertThat(invalidTimeout.getMessage(), equalTo("initialResponseTimeout must be positive: PT0S"));
        assertThrows(IllegalArgumentException.class,
                     () -> Http3ClientProtocolConfig.builder().handshakeTimeout(Duration.ZERO).buildPrototype());
        assertThrows(IllegalArgumentException.class,
                     () -> Http3ClientProtocolConfig.builder().streamOpenTimeout(Duration.ZERO).buildPrototype());
        Duration excessiveDuration = Duration.ofSeconds(Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,
                     () -> Http3ClientProtocolConfig.builder()
                             .initialResponseTimeout(excessiveDuration)
                             .handshakeTimeout(excessiveDuration)
                             .buildPrototype());
        assertThrows(IllegalArgumentException.class,
                     () -> Http3ClientProtocolConfig.builder().handshakeTimeout(excessiveDuration).buildPrototype());
        assertThrows(IllegalArgumentException.class,
                     () -> Http3ClientProtocolConfig.builder().streamOpenTimeout(excessiveDuration).buildPrototype());
        IllegalArgumentException invalidRelationship = assertThrows(
                IllegalArgumentException.class,
                () -> Http3ClientProtocolConfig.builder()
                        .initialResponseTimeout(Duration.ofSeconds(11))
                        .handshakeTimeout(Duration.ofSeconds(10))
                        .buildPrototype());
        assertThat(invalidRelationship.getMessage(),
                   equalTo("initialResponseTimeout must not exceed handshakeTimeout: PT11S > PT10S"));

        Config invalidExternalConfig = Config.just(
                ConfigSources.create(Map.of("initial-response-timeout", "PT-1S")));
        IllegalArgumentException invalidExternalTimeout = assertThrows(
                IllegalArgumentException.class,
                () -> Http3ClientProtocolConfig.create(invalidExternalConfig));
        assertThat(invalidExternalTimeout.getMessage(), equalTo("initialResponseTimeout must be positive: PT-1S"));

        Config invalidExternalSettings = Config.just(
                ConfigSources.create(Map.of("qpack-max-table-capacity", Long.toString(tooLarge))));
        assertThrows(IllegalArgumentException.class,
                     () -> Http3ClientProtocolConfig.create(invalidExternalSettings));
        Config invalidExternalRelationship = Config.just(
                ConfigSources.create(Map.of("initial-response-timeout", "PT11S",
                                            "handshake-timeout", "PT10S")));
        assertThrows(IllegalArgumentException.class,
                     () -> Http3ClientProtocolConfig.create(invalidExternalRelationship));

        Http3ClientProtocolConfig runtimeInvalidSettings =
                mock(Http3ClientProtocolConfig.class, delegatesTo(Http3ClientProtocolConfig.create()));
        when(runtimeInvalidSettings.qpackBlockedStreams()).thenReturn(tooLarge);
        assertThrows(IllegalArgumentException.class,
                     () -> Http3ExchangeClient.clientSettings(runtimeInvalidSettings));

        Http3ClientProtocolConfig runtimeInvalidTimeouts =
                mock(Http3ClientProtocolConfig.class, delegatesTo(Http3ClientProtocolConfig.create()));
        when(runtimeInvalidTimeouts.initialResponseTimeout()).thenReturn(Duration.ofSeconds(11));
        assertThrows(IllegalArgumentException.class,
                     () -> Http3ExchangeClient.clientSettings(runtimeInvalidTimeouts));
    }

    private static Map<Long, Long> controlStreamSettings(byte[] controlStreamPreamble) {
        ByteBuffer buffer = ByteBuffer.wrap(controlStreamPreamble);
        assertThat(VariableLengthEncoder.decode(buffer), equalTo(Http3StreamType.CONTROL.code()));
        assertThat(VariableLengthEncoder.decode(buffer), equalTo(Http3Protocol.FRAME_SETTINGS));

        long payloadLength = VariableLengthEncoder.decode(buffer);
        assertThat(buffer.remaining(), equalTo((int) payloadLength));

        Map<Long, Long> settings = new LinkedHashMap<>();
        while (buffer.hasRemaining()) {
            settings.put(VariableLengthEncoder.decode(buffer), VariableLengthEncoder.decode(buffer));
        }
        return settings;
    }

    private static RequestStreamFixture requestStreamFixture() {
        return requestStreamFixture(CompletableFuture.completedFuture(DATA_RECVD));
    }

    private static Http3RequestBody streamingRequestBody(int length, long declaredLength) {
        return Http3RequestBody.create(outputStream -> {
            try (outputStream) {
                outputStream.write(new byte[length]);
            }
        }, declaredLength);
    }

    private static CapturedRequest captureRequest(Http3RequestBody requestBody, long finalContentLength) {
        ClientRequestHeaders finalHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        if (finalContentLength >= 0) {
            finalHeaders.contentLength(finalContentLength);
        }
        Http3QpackContext decoder = Http3QpackContext.create(0, 0, 16_384, _ -> { });
        try (RequestExecutionFixture fixture = requestExecutionFixture(requestBody, finalHeaders)) {
            List<byte[]> frames = new ArrayList<>();
            AtomicInteger finCount = new AtomicInteger();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            doAnswer(invocation -> {
                frames.add(invocation.<BufferData>getArgument(0).readBytes());
                if (invocation.<Boolean>getArgument(1)) {
                    finCount.incrementAndGet();
                }
                return null;
            }).when(fixture.writer()).scheduleForWriting(any(BufferData.class), anyBoolean());
            when(fixture.writer().scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean()))
                    .thenAnswer(invocation -> {
                        frames.add(invocation.<BufferData>getArgument(0).readBytes());
                        if (invocation.<Boolean>getArgument(1)) {
                            finCount.incrementAndGet();
                        }
                        return CompletableFuture.completedFuture(null);
                    });

            fixture.requestStream().execute(Runnable::run, (_, cause) -> failure.set(cause));

            Headers headers = null;
            long dataLength = 0;
            for (byte[] frame : frames) {
                ByteBuffer encoded = ByteBuffer.wrap(frame);
                while (encoded.hasRemaining()) {
                    long type = VariableLengthEncoder.decode(encoded);
                    int length = Math.toIntExact(VariableLengthEncoder.decode(encoded));
                    byte[] payload = new byte[length];
                    encoded.get(payload);
                    if (type == Http3Protocol.FRAME_HEADERS) {
                        assertThat("Only one request HEADERS frame is expected", headers, nullValue());
                        headers = decoder.openStream(0).decodeHeaders(BufferData.create(payload), -1);
                    } else {
                        assertThat("Only request HEADERS and DATA frames are expected",
                                   type,
                                   equalTo(Http3Protocol.FRAME_DATA));
                        dataLength += length;
                    }
                }
            }
            return new CapturedRequest(headers, dataLength, finCount.get(), failure.get());
        } finally {
            decoder.close(new IllegalStateException("Request capture complete"));
        }
    }

    private static RequestExecutionFixture requestExecutionFixture(Http3RequestBody requestBody) {
        return requestExecutionFixture(requestBody, ClientRequestHeaders.create(WritableHeaders.create()));
    }

    private static RequestExecutionFixture requestExecutionFixture(Http3RequestBody requestBody,
                                                                   ClientRequestHeaders finalHeaders) {
        Http3ExchangeClient.ConnectionSession session = mock(Http3ExchangeClient.ConnectionSession.class);
        QuicConnection connection = mock(QuicConnection.class);
        when(connection.termination()).thenReturn(Optional.empty());
        when(session.connection()).thenReturn(connection);
        Http3QpackContext qpackContext = Http3QpackContext.create(0, 0, 16_384, _ -> {
        });
        when(session.qpackContext()).thenReturn(qpackContext);
        QuicBidiStream stream = mock(QuicBidiStream.class);
        QuicStreamReader reader = mock(QuicStreamReader.class);
        QuicStreamWriter writer = mock(QuicStreamWriter.class);
        when(stream.connectReader(any(SequentialScheduler.class))).thenReturn(reader);
        when(stream.streamId()).thenReturn(0L);
        when(stream.receivingState()).thenReturn(RECV);
        when(stream.futureSendingCompletion()).thenReturn(CompletableFuture.completedFuture(DATA_RECVD));
        when(stream.whenStopSendingReceived()).thenReturn(new CompletableFuture<>());
        when(writer.sendingState()).thenReturn(READY);
        Http3ExchangeClient.RequestData request = new Http3ExchangeClient.RequestData(
                URI.create("https://example.test"),
                Method.POST,
                finalHeaders,
                requestBody,
                Duration.ofSeconds(1),
                Duration.ZERO,
                false,
                false,
                Context.create(),
                () -> {
                });
        Http3FrameListener frameListener = Http3FrameListener.create(List.of());
        Http3RequestStream requestStream = new Http3RequestStream(
                session,
                request,
                stream,
                Http3MessageReader.response(stream,
                                            qpackContext,
                                            mock(SocketContext.class),
                                            Method.POST,
                                            16_384,
                                            frameListener),
                writer,
                frameListener,
                mock(StreamObservation.class),
                _ -> {
                },
                _ -> {
                },
                _ -> {
                });
        return new RequestExecutionFixture(writer, requestStream, qpackContext);
    }

    private static RequestStreamFixture requestStreamFixture(
            CompletableFuture<QuicSenderStream.SendingStreamState> sendCompletion) {
        Http3ExchangeClient.ConnectionSession session = mock(Http3ExchangeClient.ConnectionSession.class);
        QuicBidiStream stream = mock(QuicBidiStream.class);
        QuicStreamReader reader = mock(QuicStreamReader.class);
        QuicStreamWriter writer = mock(QuicStreamWriter.class);
        when(stream.connectReader(any(SequentialScheduler.class))).thenReturn(reader);
        when(stream.streamId()).thenReturn(0L);
        when(stream.receivingState()).thenReturn(RECV);
        when(stream.futureSendingCompletion()).thenReturn(sendCompletion);
        when(writer.sendingState()).thenReturn(READY);
        AtomicInteger completions = new AtomicInteger();
        AtomicInteger responseCancellations = new AtomicInteger();
        StreamObservation streamObservation = mock(StreamObservation.class);
        Http3RequestStream requestStream = new Http3RequestStream(
                session,
                mock(Http3ExchangeClient.RequestData.class),
                stream,
                Http3MessageReader.response(stream,
                                            Http3QpackContext.create(0, 0, 16_384, _ -> {
                                            }),
                                            mock(SocketContext.class),
                                            Method.GET,
                                            16_384,
                                            Http3FrameListener.create(List.of())),
                writer,
                Http3FrameListener.create(List.of()),
                streamObservation,
                Runnable::run,
                _ -> completions.incrementAndGet(),
                _ -> responseCancellations.incrementAndGet());
        return new RequestStreamFixture(session,
                                        stream,
                                        reader,
                                        writer,
                                        streamObservation,
                                        requestStream,
                                        completions,
                                        responseCancellations);
    }

    private record RequestStreamFixture(Http3ExchangeClient.ConnectionSession session,
                                        QuicBidiStream stream,
                                        QuicStreamReader reader,
                                        QuicStreamWriter writer,
                                        StreamObservation streamObservation,
                                        Http3RequestStream requestStream,
                                        AtomicInteger completions,
                                        AtomicInteger responseCancellations) {
    }

    private record CapturedRequest(Headers headers, long dataLength, int finCount, Throwable failure) {
    }

    private record RequestExecutionFixture(QuicStreamWriter writer,
                                            Http3RequestStream requestStream,
                                            Http3QpackContext qpackContext) implements AutoCloseable {
        @Override
        public void close() {
            try {
                requestStream.cancel();
            } finally {
                qpackContext.close(new IllegalStateException("Request fixture closed"));
            }
        }

        private Throwable execute() {
            CompletableFuture<Throwable> requestOutcome = new CompletableFuture<>();
            requestStream.execute(Runnable::run, (_, failure) -> {
                if (failure != null) {
                    requestOutcome.complete(failure);
                }
            });
            return requestOutcome.orTimeout(10, TimeUnit.SECONDS).join();
        }
    }

}
