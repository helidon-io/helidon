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

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class Http2TransportObservationTest {
    @Test
    void reportsHttp2AndBidirectionalRemoteExchange() {
        var connection = mock(ConnectionObservation.class);
        var observation = new Http2TransportObservation(connection);
        observation.protocolSelected();
        observation.openStream(false);
        verify(connection).protocolSelected("http/2");
        verify(connection).streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE);
    }

    @Test
    void responseEndWaitsForRequestEnd() {
        var delegate = mock(StreamObservation.class);
        var stream = stream(delegate);
        stream.applicationStarted();
        stream.localEnd();
        verifyNoMoreInteractions(delegate);
        stream.remoteEnd();
        stream.localEnd();
        stream.remoteReset();
        verify(delegate).close(StreamOutcome.COMPLETED);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    void requestEndWaitsForResponseEnd() {
        var delegate = mock(StreamObservation.class);
        var stream = stream(delegate);
        stream.remoteEnd();
        verifyNoMoreInteractions(delegate);
        stream.localEnd();
        verify(delegate).close(StreamOutcome.COMPLETED);
    }

    @Test
    void initialRequestEndWaitsForSuccessfulResponseEnd() {
        var delegate = mock(StreamObservation.class);
        var stream = stream(delegate, true);
        stream.remoteEnd();
        stream.applicationStarted();
        verifyNoMoreInteractions(delegate);

        stream.localEnd();
        stream.remoteEnd();
        verify(delegate).close(StreamOutcome.COMPLETED);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    void initialRequestEndStillAllowsRejectionBeforeApplication() {
        var delegate = mock(StreamObservation.class);
        var stream = stream(delegate, true);
        stream.localReset();
        stream.localEnd();
        verify(delegate).close(StreamOutcome.REJECTED);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    void rejectionResponseCanCompleteBeforeRequestBody() {
        var delegate = mock(StreamObservation.class);
        var stream = stream(delegate);
        stream.requestFailed();
        verifyNoMoreInteractions(delegate);
        stream.localEnd();
        stream.localReset();
        verify(delegate).close(StreamOutcome.REJECTED);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    void failedRequestDuringApplicationProcessingRetainsError() {
        var delegate = mock(StreamObservation.class);
        var stream = stream(delegate);
        stream.applicationStarted();
        stream.requestFailed();
        stream.localEnd();
        verify(delegate).close(StreamOutcome.ERROR);
    }

    @Test
    void failedRejectionWriteRecordsError() {
        var delegate = mock(StreamObservation.class);
        var stream = stream(delegate);
        stream.requestFailed();
        stream.fail();
        stream.localEnd();
        stream.remoteEnd();
        verify(delegate).close(StreamOutcome.ERROR);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    void localResetBeforeApplicationIsRejected() {
        var delegate = mock(StreamObservation.class);
        stream(delegate).localReset();
        verify(delegate).close(StreamOutcome.REJECTED);
    }

    @Test
    void localResetAfterApplicationIsReset() {
        var delegate = mock(StreamObservation.class);
        var stream = stream(delegate);
        stream.applicationStarted();
        stream.localReset();
        verify(delegate).close(StreamOutcome.RESET);
    }

    @Test
    void remoteResetIsNotARequestRejection() {
        var delegate = mock(StreamObservation.class);
        stream(delegate).remoteReset();
        verify(delegate).close(StreamOutcome.RESET);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stoppedPublisherLeavesOpenChildrenForPhysicalConnectionClose(boolean initiallyRemoteEnded) {
        var connection = mock(ConnectionObservation.class);
        var delegate = mock(StreamObservation.class);
        when(connection.streamOpened(any(), any())).thenReturn(delegate);
        var observation = new Http2TransportObservation(connection);
        var stream = observation.openStream(initiallyRemoteEnded);
        observation.stop();
        stream.fail();
        stream.localEnd();
        stream.remoteEnd();
        observation.protocolSelected();
        observation.openStream(initiallyRemoteEnded).fail();
        verify(connection).streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE);
        verify(connection, never()).protocolSelected(any());
        verifyNoMoreInteractions(delegate);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentStreamsPublishSequentially(boolean initiallyRemoteEnded) throws Exception {
        var connection = mock(ConnectionObservation.class);
        var callbacks = new AtomicInteger();
        var overlaps = new AtomicInteger();
        var closed = new AtomicInteger();
        when(connection.streamOpened(any(), any())).thenAnswer(_ -> {
            if (callbacks.incrementAndGet() != 1) {
                overlaps.incrementAndGet();
            }
            Thread.yield();
            callbacks.decrementAndGet();
            return (StreamObservation) outcome -> {
                if (callbacks.incrementAndGet() != 1) {
                    overlaps.incrementAndGet();
                }
                Thread.yield();
                closed.incrementAndGet();
                callbacks.decrementAndGet();
            };
        });
        var observation = new Http2TransportObservation(connection);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 100; i++) {
                tasks.add(executor.submit(() -> {
                    var stream = observation.openStream(initiallyRemoteEnded);
                    stream.applicationStarted();
                    stream.remoteEnd();
                    stream.localEnd();
                }));
            }
            for (var task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        }
        assertThat("Every exchange closed exactly once", closed.get(), is(100));
        assertThat("Callbacks for a connection cannot overlap", overlaps.get(), is(0));
    }

    private static Http2TransportObservation.Stream stream(StreamObservation delegate) {
        return stream(delegate, false);
    }

    private static Http2TransportObservation.Stream stream(StreamObservation delegate, boolean initiallyRemoteEnded) {
        var connection = mock(ConnectionObservation.class);
        when(connection.streamOpened(any(), any())).thenReturn(delegate);
        return new Http2TransportObservation(connection).openStream(initiallyRemoteEnded);
    }
}
