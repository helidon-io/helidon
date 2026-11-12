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
package io.helidon.webclient;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.context.Context;
import io.helidon.common.http.DataChunk;
import io.helidon.media.common.MediaContext;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.helidon.webclient.WebClientRequestBuilderImpl.COMPLETED;
import static io.helidon.webclient.WebClientRequestBuilderImpl.IN_USE;
import static io.helidon.webclient.WebClientRequestBuilderImpl.RECEIVED;
import static io.helidon.webclient.WebClientRequestBuilderImpl.REQUEST;
import static io.helidon.webclient.WebClientRequestBuilderImpl.REQUEST_CONTENT;
import static io.helidon.webclient.WebClientRequestBuilderImpl.REQUEST_ID;
import static io.helidon.webclient.WebClientRequestBuilderImpl.RESULT;
import static io.helidon.webclient.WebClientRequestBuilderImpl.RETURN;
import static io.helidon.webclient.WebClientRequestBuilderImpl.WILL_CLOSE;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http1ResponseFramingTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void malformedChunkFailsResponseAndRetiresConnection() throws Exception {
        CompletableFuture<WebClientResponse> result = new CompletableFuture<>();
        EmbeddedChannel channel = newChannel(result, true);
        try {
            writeResponse(channel, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n"
                    + "Connection: keep-alive\r\nContent-Type: application/octet-stream\r\n\r\n1\r\nAx");
            WebClientResponse response = result.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            CompletableFuture<byte[]> body = response.content().as(byte[].class).toCompletableFuture();

            writeResponse(channel, "y");

            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> body.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertThat(failure.getCause(), instanceOf(WebClientException.class));
            assertThat(channel.isOpen(), is(false));
            response.close().await(TIMEOUT);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void invalidResponseHeadersFailBeforeDecompression() throws Exception {
        CompletableFuture<WebClientResponse> result = new CompletableFuture<>();
        EmbeddedChannel channel = newChannel(result, true);
        try {
            DefaultHttpResponse invalid = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            invalid.setDecoderResult(DecoderResult.failure(new IllegalArgumentException("invalid header")));
            channel.writeInbound(invalid);

            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> result.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertThat(failure.getCause(), instanceOf(WebClientException.class));
            assertThat(channel.isOpen(), is(false));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void invalidResponseCancelsUnfinishedUpload(int chunks) throws Exception {
        CompletableFuture<WebClientResponse> result = new CompletableFuture<>();
        EmbeddedChannel channel = newChannel(result, true, false, TIMEOUT);
        AtomicBoolean canceled = new AtomicBoolean();
        AtomicInteger released = new AtomicInteger();
        CompletableFuture<WebClientServiceRequest> sent = new CompletableFuture<>();
        try {
            RequestContentSubscriber upload = new RequestContentSubscriber(
                    new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/"), channel, result, sent, true);
            channel.attr(REQUEST_CONTENT).set(upload);
            upload.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long count) {
                    assertThat(count, greaterThan(0L));
                }

                @Override
                public void cancel() {
                    canceled.set(true);
                }
            });
            for (int chunk = 0; chunk < chunks; chunk++) {
                upload.onNext(DataChunk.create(true, true, released::incrementAndGet, ByteBuffer.wrap(new byte[] {'a'})));
            }
            channel.runPendingTasks();
            Object outbound;
            while ((outbound = channel.readOutbound()) != null) {
                ReferenceCountUtil.release(outbound);
            }
            assertThat(sent.isDone(), is(false));
            assertThat(released.get(), is(chunks == 1 ? 0 : chunks));

            DefaultHttpResponse invalid = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            invalid.setDecoderResult(DecoderResult.failure(new IllegalArgumentException("invalid header")));
            channel.writeInbound(invalid);

            ExecutionException responseFailure = assertThrows(ExecutionException.class,
                    () -> result.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertThat(responseFailure.getCause(), instanceOf(WebClientException.class));
            assertThat(canceled.get(), is(true));
            ExecutionException sendFailure = assertThrows(ExecutionException.class,
                    () -> sent.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertThat(sendFailure.getCause(), is(responseFailure.getCause()));
            assertThat(channel.attr(RECEIVED).get().isCompletedExceptionally(), is(true));
            assertThat(channel.attr(COMPLETED).get().isCompletedExceptionally(), is(true));
            assertThat(released.get(), is(chunks));
            assertThat(channel.isOpen(), is(false));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void closeDelimitedResponseWithoutContentTypeIsNotDiscarded() throws Exception {
        CompletableFuture<WebClientResponse> result = new CompletableFuture<>();
        EmbeddedChannel channel = newChannel(result, false);
        try {
            writeResponse(channel, "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\nhello");
            WebClientResponse response = result.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            CompletableFuture<byte[]> body = response.content().as(byte[].class).toCompletableFuture();

            assertThat(channel.isOpen(), is(true));
            channel.finish();

            assertThat(new String(body.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), StandardCharsets.US_ASCII), is("hello"));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void chunkedResponseWithoutContentTypeIsNotDiscarded() throws Exception {
        CompletableFuture<WebClientResponse> result = new CompletableFuture<>();
        EmbeddedChannel channel = newChannel(result, true);
        try {
            writeResponse(channel, "HTTP/1.1 200 OK\r\nTransfer-Encoding: CHUNKED\r\n"
                    + "Connection: keep-alive\r\n\r\n2\r\nOK\r\n0\r\n\r\n");
            WebClientResponse response = result.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            assertThat(new String(response.content().as(byte[].class).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                                  StandardCharsets.US_ASCII), is("OK"));
            assertThat(channel.isOpen(), is(true));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void informationalResponseDoesNotCompleteRequest() throws Exception {
        CompletableFuture<WebClientResponse> result = new CompletableFuture<>();
        EmbeddedChannel channel = newChannel(result, false);
        try {
            writeResponse(channel, "HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 200 OK\r\n"
                    + "Content-Length: 2\r\n\r\nOK");
            WebClientResponse response = result.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            assertThat(response.status().code(), is(200));
            assertThat(new String(response.content().as(byte[].class).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                                  StandardCharsets.US_ASCII), is("OK"));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void informationalResponseOnReusedConnectionReadsFinalResponse() throws Exception {
        CompletableFuture<WebClientResponse> firstResult = new CompletableFuture<>();
        EmbeddedChannel channel = newChannel(firstResult, true);
        AtomicBoolean deliverFinalResponse = new AtomicBoolean();
        channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
            @Override
            public void read(ChannelHandlerContext context) {
                context.read();
                if (deliverFinalResponse.compareAndSet(true, false)) {
                    context.executor().execute(() -> writeResponse(channel,
                            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK"));
                }
            }
        });
        try {
            writeResponse(channel, "HTTP/1.1 204 No Content\r\nConnection: keep-alive\r\n\r\n");
            firstResult.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).close().await(TIMEOUT);

            CompletableFuture<WebClientResponse> secondResult = new CompletableFuture<>();
            channel.attr(RESULT).set(secondResult);
            channel.attr(RECEIVED).set(new CompletableFuture<>());
            channel.attr(COMPLETED).set(new CompletableFuture<>());
            channel.attr(REQUEST_ID).set(2L);
            channel.attr(RETURN).get().set(false);
            channel.attr(IN_USE).get().set(true);
            channel.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

            deliverFinalResponse.set(true);
            writeResponse(channel, "HTTP/1.1 103 Early Hints\r\n\r\n");
            channel.runPendingTasks();

            WebClientResponse response = secondResult.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(response.status().code(), is(200));
            assertThat(new String(response.content().as(byte[].class).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                                  StandardCharsets.US_ASCII), is("OK"));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @CsvSource({"false, 0", "false, 1000", "true, 0", "true, 1000"})
    void peerCloseAfterInformationalResponseFailsPendingRequest(boolean reuse, long timeoutMillis) throws Exception {
        CompletableFuture<WebClientResponse> firstResult = new CompletableFuture<>();
        CompletableFuture<WebClientResponse> result = reuse ? new CompletableFuture<>() : firstResult;
        EmbeddedChannel channel = newChannel(firstResult, true, true, Duration.ofMillis(timeoutMillis));
        channel.freezeTime();
        try {
            if (reuse) {
                writeResponse(channel, "HTTP/1.1 204 No Content\r\nConnection: keep-alive\r\n\r\n");
                firstResult.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).close().await(TIMEOUT);
                channel.attr(RESULT).set(result);
                channel.attr(RECEIVED).set(new CompletableFuture<>());
                channel.attr(COMPLETED).set(new CompletableFuture<>());
                channel.attr(REQUEST_ID).set(2L);
                channel.attr(RETURN).get().set(false);
                channel.attr(IN_USE).get().set(true);
                channel.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));
            }
            writeResponse(channel, "HTTP/1.1 103 Early Hints\r\n\r\n");
            assertThat(result.isDone(), is(false));

            channel.close().syncUninterruptibly();

            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> result.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertThat(failure.getCause(), instanceOf(WebClientException.class));
            assertThat(failure.getCause().getMessage(), containsString("final HTTP response"));
            assertThat(channel.attr(RECEIVED).get().isCompletedExceptionally(), is(true));
            assertThat(channel.attr(COMPLETED).get().isCompletedExceptionally(), is(true));
            assertThat(channel.isOpen(), is(false));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void repeatedInformationalResponsesTimeoutAndCancelUpload() throws Exception {
        CompletableFuture<WebClientResponse> result = new CompletableFuture<>();
        EmbeddedChannel channel = newChannel(result, true, false, Duration.ofMillis(100));
        channel.freezeTime();
        AtomicBoolean canceled = new AtomicBoolean();
        CompletableFuture<WebClientServiceRequest> sent = new CompletableFuture<>();
        try {
            RequestContentSubscriber upload = new RequestContentSubscriber(
                    new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/"), channel, result, sent, true);
            channel.attr(REQUEST_CONTENT).set(upload);
            upload.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long count) {
                    assertThat(count, greaterThan(0L));
                }

                @Override
                public void cancel() {
                    canceled.set(true);
                }
            });
            upload.onNext(DataChunk.create(true, true, ByteBuffer.wrap(new byte[] {'a'})));
            upload.onNext(DataChunk.create(true, true, ByteBuffer.wrap(new byte[] {'b'})));

            writeResponse(channel, "HTTP/1.1 103 Early Hints\r\n\r\n");
            channel.advanceTimeBy(60, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();
            assertThat(result.isDone(), is(false));
            writeResponse(channel, "HTTP/1.1 100 Continue\r\n\r\n");
            channel.advanceTimeBy(41, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();

            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> result.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertThat(failure.getCause(), instanceOf(WebClientException.class));
            assertThat(failure.getCause().getMessage(), containsString("final HTTP response"));
            assertThat(channel.attr(RECEIVED).get().isCompletedExceptionally(), is(true));
            assertThat(channel.attr(COMPLETED).get().isCompletedExceptionally(), is(true));
            assertThat(sent.isCompletedExceptionally(), is(true));
            assertThat(canceled.get(), is(true));
            assertThat(channel.isOpen(), is(false));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void completedInformationalDeadlineCannotExpireReusedConnection() throws Exception {
        CompletableFuture<WebClientResponse> firstResult = new CompletableFuture<>();
        EmbeddedChannel channel = newChannel(firstResult, true, true, Duration.ofMillis(100));
        channel.freezeTime();
        try {
            writeResponse(channel, "HTTP/1.1 103 Early Hints\r\n\r\n");
            channel.advanceTimeBy(60, TimeUnit.MILLISECONDS);
            writeResponse(channel, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: keep-alive\r\n\r\nOK");
            WebClientResponse first = firstResult.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(first.content().as(String.class).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is("OK"));
            first.close().await(TIMEOUT);

            CompletableFuture<WebClientResponse> secondResult = new CompletableFuture<>();
            channel.attr(RESULT).set(secondResult);
            channel.attr(RECEIVED).set(new CompletableFuture<>());
            channel.attr(COMPLETED).set(new CompletableFuture<>());
            channel.attr(REQUEST_ID).set(2L);
            channel.attr(RETURN).get().set(false);
            channel.attr(IN_USE).get().set(true);
            channel.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));
            writeResponse(channel, "HTTP/1.1 103 Early Hints\r\n\r\n");
            channel.advanceTimeBy(50, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();

            assertThat(secondResult.isDone(), is(false));
            assertThat(channel.isOpen(), is(true));
            writeResponse(channel, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: keep-alive\r\n\r\nOK");
            WebClientResponse second = secondResult.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(second.content().as(String.class).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is("OK"));
            second.close().await(TIMEOUT);
            channel.advanceTimeBy(200, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();
            assertThat(channel.isOpen(), is(true));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void nonpositiveReadTimeoutDisablesInformationalDeadline(long timeoutMillis) throws Exception {
        CompletableFuture<WebClientResponse> result = new CompletableFuture<>();
        EmbeddedChannel channel = newChannel(result, true, true, Duration.ofMillis(timeoutMillis));
        channel.freezeTime();
        try {
            writeResponse(channel, "HTTP/1.1 103 Early Hints\r\n\r\n");
            channel.advanceTimeBy(1, TimeUnit.DAYS);
            channel.runScheduledPendingTasks();
            assertThat(result.isDone(), is(false));
            assertThat(channel.isOpen(), is(true));

            writeResponse(channel, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
            assertThat(result.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).status().code(), is(200));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"204 No Content", "304 Not Modified"})
    void nettyCompletesBodylessResponses(String status) throws Exception {
        CompletableFuture<WebClientResponse> result = new CompletableFuture<>();
        EmbeddedChannel channel = newChannel(result, true);
        try {
            writeResponse(channel, "HTTP/1.1 " + status + "\r\nConnection: keep-alive\r\n\r\n");
            WebClientResponse response = result.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            assertThat(response.content().as(byte[].class).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).length, is(0));
            assertThat(channel.isOpen(), is(true));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void earlyFinalResponseCancelsUploadWithoutLosingResponse() throws Exception {
        CompletableFuture<WebClientResponse> result = new CompletableFuture<>();
        EmbeddedChannel channel = newChannel(result, true, false, TIMEOUT);
        AtomicBoolean canceled = new AtomicBoolean();
        CompletableFuture<WebClientServiceRequest> sent = new CompletableFuture<>();
        try {
            RequestContentSubscriber upload = new RequestContentSubscriber(
                    new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/"), channel, result, sent, true);
            channel.attr(REQUEST_CONTENT).set(upload);
            upload.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long count) {
                    assertThat(count, greaterThan(0L));
                }

                @Override
                public void cancel() {
                    canceled.set(true);
                }
            });
            upload.onNext(DataChunk.create(true, true, ByteBuffer.wrap(new byte[] {'a'})));
            upload.onNext(DataChunk.create(true, true, ByteBuffer.wrap(new byte[] {'b'})));

            writeResponse(channel, "HTTP/1.1 413 Payload Too Large\r\nConnection: keep-alive\r\n"
                    + "Content-Length: 3\r\n\r\nbad");

            WebClientResponse response = result.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(response.status().code(), is(413));
            assertThat(new String(response.content().as(byte[].class).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                                  StandardCharsets.US_ASCII), is("bad"));
            assertThat(canceled.get(), is(true));
            ExecutionException sendFailure = assertThrows(ExecutionException.class,
                    () -> sent.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertThat(sendFailure.getCause(), instanceOf(WebClientException.class));
            assertThat(channel.isOpen(), is(false));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static EmbeddedChannel newChannel(CompletableFuture<WebClientResponse> result, boolean keepAlive) {
        return newChannel(result, keepAlive, true, TIMEOUT);
    }

    private static EmbeddedChannel newChannel(CompletableFuture<WebClientResponse> result,
                                              boolean keepAlive,
                                              boolean sendRequest,
                                              Duration readTimeout) {
        URI uri = URI.create("http://localhost/entity");
        RequestConfiguration configuration = RequestConfiguration.builder(uri)
                .context(Context.create())
                .readerContext(MediaContext.create().readerContext())
                .readTimeout(readTimeout)
                .build();
        WebClientRequestImpl request = mock(WebClientRequestImpl.class);
        when(request.configuration()).thenReturn(configuration);

        EmbeddedChannel channel = new EmbeddedChannel(new HttpClientCodec(),
                                                      new NettyResponseDecoderHandler(),
                                                      new HttpContentDecompressor(),
                                                      new NettyClientHandler());
        channel.attr(REQUEST).set(request);
        channel.attr(REQUEST_ID).set(1L);
        channel.attr(RECEIVED).set(new CompletableFuture<>());
        channel.attr(COMPLETED).set(new CompletableFuture<>());
        channel.attr(RESULT).set(result);
        channel.attr(WILL_CLOSE).set(!keepAlive);
        if (keepAlive) {
            channel.attr(RETURN).set(new AtomicBoolean(false));
            channel.attr(IN_USE).set(new AtomicBoolean(true));
        }
        if (sendRequest) {
            channel.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));
        }
        Object outbound;
        while ((outbound = channel.readOutbound()) != null) {
            ReferenceCountUtil.release(outbound);
        }
        return channel;
    }

    private static void writeResponse(EmbeddedChannel channel, String response) {
        channel.writeInbound(Unpooled.copiedBuffer(response, StandardCharsets.US_ASCII));
    }
}
