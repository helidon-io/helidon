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

package io.helidon.webserver.http2;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2GoAway;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanEncoder;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ErrorHandling;
import io.helidon.webserver.HttpTransportObserverSupport.ConnectionObservationContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.http2.spi.SubProtocolResult;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class Http2CompletionRaceTest {
    @Test
    void peerGoAwayWaitsForSuccessfulTerminalResponseObservation() throws InterruptedException {
        CountDownLatch terminalResponseWritten = new CountDownLatch(1);
        CountDownLatch releaseTerminalWrite = new CountDownLatch(1);
        CountDownLatch peerGoAwayProcessed = new CountDownLatch(1);
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        Http2Headers requestHeaders = Http2Headers.create(WritableHeaders.create());
        requestHeaders.method(Method.GET);
        requestHeaders.path("/grpc");
        requestHeaders.scheme("https");
        requestHeaders.authority("localhost:443");
        BufferData headersData = BufferData.growing(512);
        requestHeaders.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                             Http2HuffmanEncoder.create(),
                             headersData);
        input.add(BufferData.create(Http2FrameHeader.create(headersData.available(),
                                                           Http2FrameTypes.HEADERS,
                                                           Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS
                                                                                               | Http2Flag.END_OF_STREAM),
                                                           1).write(), headersData).readBytes());
        Http2FrameData goAway = new Http2GoAway(1, Http2ErrorCode.NO_ERROR, "done")
                .toFrameData(Http2Settings.create(), 0, Http2Flag.NoFlags.create());
        input.add(BufferData.create(goAway.header().write(), goAway.data()).readBytes());
        DataReader reader = DataReader.create(() -> {
            byte[] bytes = input.poll();
            if (bytes != null && Http2FrameHeader.create(BufferData.create(bytes)).type() == Http2FrameType.GO_AWAY) {
                await(terminalResponseWritten, "peer must receive terminal response before sending GOAWAY");
            }
            return bytes;
        });
        DataWriter writer = mock(DataWriter.class);
        doAnswer(invocation -> {
            Http2FrameHeader header = Http2FrameHeader.create(invocation.<BufferData>getArgument(0).copy());
            if (header.type() == Http2FrameType.HEADERS && (header.flags() & Http2Flag.END_OF_STREAM) != 0) {
                // The peer has received these bytes, but writeNow has not returned to publish local completion.
                terminalResponseWritten.countDown();
                awaitSuccessfulWrite(releaseTerminalWrite);
            }
            return null;
        }).when(writer).writeNow(any(BufferData.class));
        Queue<StreamOutcome> outcomes = new ConcurrentLinkedQueue<>();
        ConnectionObservation observation = mock(ConnectionObservation.class);
        when(observation.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE)).thenReturn(outcomes::add);
        ConnectionContext ctx = mock(ConnectionContext.class,
                                     withSettings().extraInterfaces(ConnectionObservationContext.class));
        ConnectionObservationContext observationContext = (ConnectionObservationContext) ctx;
        when(observationContext.httpTransportObservation()).thenReturn(observation);
        doAnswer(_ -> {
            peerGoAwayProcessed.countDown();
            return null;
        }).when(observationContext).httpTransportOutcome(ConnectionOutcome.REMOTE_CLOSE);
        when(ctx.router()).thenReturn(Router.empty());
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.config()).thenReturn(ListenerConfig.builder()
                                                         .shutdownGracePeriod(Duration.ofSeconds(10))
                                                         .errorHandling(ErrorHandling.builder().includeEntity(false).build())
                                                         .build());
        when(listenerContext.directHandlers()).thenReturn(DirectHandlers.create());
        when(ctx.listenerContext()).thenReturn(listenerContext);
        when(ctx.dataReader()).thenReturn(reader);
        when(ctx.dataWriter()).thenReturn(writer);
        when(ctx.sniContext()).thenReturn(Optional.empty());
        when(ctx.proxyProtocolData()).thenReturn(Optional.empty());
        PeerInfo peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
        when(ctx.remotePeer()).thenReturn(peerInfo);
        AtomicReference<Thread> streamThread = new AtomicReference<>();
        AtomicReference<Throwable> streamFailure = new AtomicReference<>();
        ExecutorService executor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            streamThread.set(Thread.ofVirtual().start(() -> {
                try {
                    runnable.run();
                } catch (Throwable failure) {
                    streamFailure.set(failure);
                }
            }));
            return null;
        }).when(executor).submit(any(Runnable.class));
        when(ctx.executor()).thenReturn(executor);
        Http2SubProtocolSelector selector = (_, _, _, streamWriter, streamId, _, _, _, _, _) -> {
            var handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
            when(handler.streamState()).thenReturn(Http2StreamState.CLOSED);
            doAnswer(_ -> {
                try {
                    streamWriter.writeHeaders(Http2Headers.create(WritableHeaders.create()).status(Status.OK_200),
                                              streamId,
                                              Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                              FlowControl.Outbound.NOOP);
                } catch (Throwable failure) {
                    streamFailure.set(failure);
                    throw failure;
                }
                return null;
            }).when(handler).init();
            return new SubProtocolResult(true, handler);
        };
        Http2Connection connection = new Http2Connection(ctx, Http2Config.create(), List.of(selector));
        AtomicReference<Throwable> connectionFailure = new AtomicReference<>();
        AtomicReference<List<StreamOutcome>> outcomesWhenHandleReturned = new AtomicReference<>();
        Thread connectionThread = Thread.ofVirtual().start(() -> {
            try {
                connection.handle(mock(Limit.class));
                outcomesWhenHandleReturned.set(List.copyOf(outcomes));
            } catch (Throwable failure) {
                connectionFailure.set(failure);
            }
        });
        try {
            await(peerGoAwayProcessed, "connection must process peer GOAWAY during terminal write");
            awaitTeardown(connectionThread);
            releaseTerminalWrite.countDown();
            assertThat("connection handler must terminate", connectionThread.join(Duration.ofSeconds(5)), is(true));
            assertThat("terminal writer must terminate", streamThread.get().join(Duration.ofSeconds(5)), is(true));
            assertAll(
                    () -> assertThat("connection worker failure", connectionFailure.get(), is(nullValue())),
                    () -> assertThat("stream worker failure", streamFailure.get(), is(nullValue())),
                    () -> assertThat("terminal response outcome must be observed before handle returns",
                                     outcomesWhenHandleReturned.get(), is(List.of(StreamOutcome.COMPLETED))),
                    () -> assertThat("terminal response outcome is observed exactly once",
                                     List.copyOf(outcomes), is(List.of(StreamOutcome.COMPLETED)))
            );
        } finally {
            releaseTerminalWrite.countDown();
            terminalResponseWritten.countDown();
            connection.close(true);
            Thread runner = streamThread.get();
            boolean streamJoined = runner == null || runner.join(Duration.ofSeconds(5));
            boolean connectionJoined = connectionThread.join(Duration.ofSeconds(5));
            assertAll(
                    () -> assertThat("stream cleanup must terminate", streamJoined, is(true)),
                    () -> assertThat("connection cleanup must terminate", connectionJoined, is(true))
            );
        }
    }

    private static void awaitTeardown(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        Thread.State state;
        do {
            state = thread.getState();
            if (state == Thread.State.TERMINATED || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        throw new AssertionError("connection must terminate or await pending completion; state: " + state);
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            assertThat(message, latch.await(5, TimeUnit.SECONDS), is(true));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, failure);
        }
    }

    private static void awaitSuccessfulWrite(CountDownLatch release) {
        // A successful socket write can return after teardown interrupts the stream thread.
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (true) {
                try {
                    assertThat("terminal write must be released",
                               release.await(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS), is(true));
                    return;
                } catch (InterruptedException _) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
