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

package io.helidon.webclient.api;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.http.HttpTransportObserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Timeout(20)
class ProtocolDiscoveryConnectionsTest {
    @Test
    void closingOwnerClosesUnhandedProbeOnce() {
        var owner = new ProtocolDiscoveryConnections();
        var registration = owner.registration(HttpTransportObserver.noop());
        var connection = connection(registration);
        registration.connect(connection);

        owner.closeResource();
        owner.closeResource();

        verify(connection).closeResource();
        assertThrows(IllegalStateException.class, () -> owner.registration(HttpTransportObserver.noop()));
        assertThrows(IllegalStateException.class, registration::handoff);
    }

    @Test
    void protocolOwnedConnectionSurvivesDiscoveryOwnerShutdown() {
        var owner = new ProtocolDiscoveryConnections();
        var registration = owner.registration(HttpTransportObserver.noop());
        var connection = connection(registration);
        registration.connect(connection);
        registration.handoff();

        owner.closeResource();

        verify(connection, never()).closeResource();
        connection.closeResource();
        owner.closeResource();
        verify(connection).closeResource();
    }

    @Test
    void closedRegistrationCannotConnect() {
        var owner = new ProtocolDiscoveryConnections();
        var registration = owner.registration(HttpTransportObserver.noop());
        var connection = connection(registration);
        owner.closeResource();

        assertThrows(IllegalStateException.class, () -> registration.connect(connection));

        verify(connection, never()).connect();
        verify(connection, never()).closeResource();
    }

    @Test
    void ownerShutdownWaitsForConnectBeforeClosingPhysicalConnection() throws Exception {
        var owner = new ProtocolDiscoveryConnections();
        var registration = owner.registration(HttpTransportObserver.noop());
        var connection = connection(registration);
        var connecting = new CountDownLatch(1);
        var finishConnect = new CountDownLatch(1);
        var shutdownStarted = new CountDownLatch(1);
        var connected = new AtomicBoolean();
        when(connection.connect()).thenAnswer(_ -> {
            connecting.countDown();
            if (!finishConnect.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Connect was not released by the test");
            }
            connected.set(true);
            return connection;
        });
        doAnswer(_ -> {
            assertThat("Physical close must follow completion of connect", connected.get(), is(true));
            registration.closed(connection);
            return null;
        }).when(connection).closeResource();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var connect = executor.submit(() -> registration.connect(connection));
            try {
                assertThat(connecting.await(10, TimeUnit.SECONDS), is(true));
                var close = executor.submit(() -> {
                    shutdownStarted.countDown();
                    owner.closeResource();
                });
                assertThat(shutdownStarted.await(10, TimeUnit.SECONDS), is(true));
                finishConnect.countDown();
                connect.get(10, TimeUnit.SECONDS);
                close.get(10, TimeUnit.SECONDS);
            } finally {
                finishConnect.countDown();
                owner.closeResource();
            }
        }
        verify(connection).closeResource();
        assertThrows(IllegalStateException.class, registration::handoff);
    }

    private static ClientConnection connection(ProtocolDiscoveryConnections.Registration registration) {
        var connection = mock(ClientConnection.class);
        when(connection.connect()).thenReturn(connection);
        doAnswer(_ -> {
            registration.closed(connection);
            return null;
        }).when(connection).closeResource();
        return connection;
    }
}
