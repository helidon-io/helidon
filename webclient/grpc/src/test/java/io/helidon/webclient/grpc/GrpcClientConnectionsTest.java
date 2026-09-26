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

package io.helidon.webclient.grpc;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.http.HttpTransportObserver;
import io.helidon.webclient.api.ClientConnection;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcClientConnectionsTest {
    @Test
    void closedConnectionIsNotAddedAfterConnect() {
        var connections = new GrpcClientConnections();
        var registration = connections.registration(HttpTransportObserver.noop());
        var connection = new TestConnection(registration::closed);

        assertThrows(IllegalStateException.class, () -> registration.connect(connection));
        assertThat(connection.closes.get(), is(1));

        connections.closeResource();
        assertThat("closed connection must not remain tracked", connection.closes.get(), is(1));
    }

    @Test
    void ownerClosedDuringConnectClosesLateConnection() throws InterruptedException {
        var connections = new GrpcClientConnections();
        var registration = connections.registration(HttpTransportObserver.noop());
        CountDownLatch connecting = new CountDownLatch(1);
        CountDownLatch finishConnect = new CountDownLatch(1);
        var connection = new TestConnection(_ -> {
            connecting.countDown();
            try {
                assertThat(finishConnect.await(5, TimeUnit.SECONDS), is(true));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
        });
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = Thread.ofVirtual().start(() -> {
            try {
                registration.connect(connection);
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        try {
            assertThat(connecting.await(5, TimeUnit.SECONDS), is(true));
            connections.closeResource();
            finishConnect.countDown();
            assertThat(thread.join(Duration.ofSeconds(5)), is(true));
            assertThat(failure.get(), instanceOf(IllegalStateException.class));
            assertThat(connection.closes.get(), is(1));
        } finally {
            finishConnect.countDown();
            thread.join(TimeUnit.SECONDS.toMillis(5));
            connections.closeResource();
        }
    }

    @Test
    void closingOwnerClosesOnlyActiveConnections() {
        var connections = new GrpcClientConnections();
        var firstRegistration = connections.registration(HttpTransportObserver.noop());
        var secondRegistration = connections.registration(HttpTransportObserver.noop());
        var first = new TestConnection(_ -> { });
        var second = new TestConnection(_ -> { });
        firstRegistration.connect(first);
        secondRegistration.connect(second);
        first.closeResource();
        firstRegistration.closed(first);

        connections.closeResource();
        connections.closeResource();

        assertThat(first.closes.get(), is(1));
        assertThat(second.closes.get(), is(1));
    }

    private static final class TestConnection implements ClientConnection {
        private final AtomicInteger closes = new AtomicInteger();
        private final Consumer<TestConnection> onConnect;

        private TestConnection(Consumer<TestConnection> onConnect) {
            this.onConnect = onConnect;
        }

        @Override
        public ClientConnection connect() {
            onConnect.accept(this);
            return this;
        }

        @Override
        public void closeResource() {
            closes.incrementAndGet();
        }

        @Override
        public DataReader reader() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DataWriter writer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String channelId() {
            return "test";
        }

        @Override
        public HelidonSocket helidonSocket() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void readTimeout(Duration readTimeout) {
        }
    }
}
