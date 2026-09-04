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

package io.helidon.quic;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import io.helidon.quic.frame.NewConnectionIDFrame;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.frame.RetireConnectionIDFrame;
import io.helidon.quic.packet.QuicPacket;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalConnIdManagerTest {
    @Test
    void failedRegistrationDoesNotConsumeConnectionIdSequence() throws Exception {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicConfig config = QuicConfig.create();
        QuicInstance instance = mock(QuicInstance.class);
        when(instance.executor()).thenReturn(Runnable::run);
        when(instance.isClient()).thenReturn(false);
        QuicEndpoint endpoint = QuicEndpoint.QuicEndpointFactory.create()
                .createVirtualThreadedEndpoint(instance,
                                               QuicRuntimeConfig.create(config),
                                               "local-connection-id-sequence-test",
                                               new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                               new QuicTimerQueue(() -> {
                                               }, () -> "quic.test.local-connection-id-sequence.timer"));
        QuicConnectionId initial = PeerConnectionId.create(new byte[] {1});
        when(connection.endpoint()).thenReturn(endpoint);
        QuicEndpointRouteLifecycle lifecycle = new QuicEndpointRouteLifecycle(connection);
        when(connection.routeLifecycle()).thenReturn(lifecycle);
        when(connection.isOpen()).thenReturn(true);
        when(connection.withStreamDispatchLock(any())).thenAnswer(invocation ->
                invocation.<BooleanSupplier>getArgument(0).getAsBoolean());
        when(connection.withConnectionIdLock(any())).thenAnswer(invocation ->
                invocation.<BooleanSupplier>getArgument(0).getAsBoolean());

        LocalConnIdManager manager = new LocalConnIdManager(connection, initial);
        when(connection.connectionIds()).thenAnswer(invocation -> manager.connectionIds());
        when(connection.freezeEndpointRoutes()).thenAnswer(invocation ->
                new QuicConnectionImpl.EndpointRoutes(manager.connectionIds(), List.of()));

        try (endpoint) {
            endpoint.registerNewConnection(connection);
            QuicPacketReceiver temporaryOwner = mock(QuicPacketReceiver.class);
            assertThat(lifecycle.transfer(connection, temporaryOwner), is(true));

            assertThat(manager.nextFrame(64), is((QuicFrame) null));
            assertThat(lifecycle.transfer(temporaryOwner, connection), is(true));
            NewConnectionIDFrame firstAdvertised = (NewConnectionIDFrame) manager.nextFrame(64);

            assertThat(firstAdvertised.sequenceNumber(), is(1L));
        }
    }

    @Test
    void finalConnectionIdRetirementIsRetriedAfterReplacementRegistration() throws Exception {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicConfig config = QuicConfig.create();
        QuicInstance instance = mock(QuicInstance.class);
        when(instance.executor()).thenReturn(Runnable::run);
        when(instance.isClient()).thenReturn(false);
        QuicEndpoint endpoint = QuicEndpoint.QuicEndpointFactory.create()
                .createVirtualThreadedEndpoint(instance,
                                               QuicRuntimeConfig.create(config),
                                               "local-connection-id-retirement-test",
                                               new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                               new QuicTimerQueue(() -> {
                                               }, () -> "quic.test.local-connection-id-retirement.timer"));
        QuicConnectionId initial = PeerConnectionId.create(new byte[] {1});
        when(connection.endpoint()).thenReturn(endpoint);
        QuicEndpointRouteLifecycle lifecycle = spy(new QuicEndpointRouteLifecycle(connection));
        when(connection.routeLifecycle()).thenReturn(lifecycle);
        when(connection.isOpen()).thenReturn(true);
        when(connection.withStreamDispatchLock(any())).thenAnswer(invocation ->
                invocation.<BooleanSupplier>getArgument(0).getAsBoolean());
        when(connection.withConnectionIdLock(any())).thenAnswer(invocation ->
                invocation.<BooleanSupplier>getArgument(0).getAsBoolean());

        LocalConnIdManager manager = new LocalConnIdManager(connection, initial);
        when(connection.connectionIds()).thenAnswer(invocation -> manager.connectionIds());
        when(connection.freezeEndpointRoutes()).thenAnswer(invocation ->
                new QuicConnectionImpl.EndpointRoutes(manager.connectionIds(), List.of()));

        try (endpoint) {
            endpoint.registerNewConnection(connection);
            manager.handleRetireConnectionIdFrame(PeerConnectionId.create(new byte[] {2}),
                                                  QuicPacket.PacketType.ONERTT,
                                                  RetireConnectionIDFrame.create(0));
            manager.handleRetireConnectionIdFrame(PeerConnectionId.create(new byte[] {2}),
                                                  QuicPacket.PacketType.ONERTT,
                                                  RetireConnectionIDFrame.create(0));

            assertThat(manager.connectionIds(), contains(initial));
            assertThat(endpoint.findQuicConnectionFor(null, initial.asReadOnlyBuffer(), true), is(connection));
            verify(lifecycle, times(1)).retireConnectionId(connection);

            assertThat(manager.nextFrame(64), is(notNullValue()));
            List<QuicConnectionId> replacement = manager.connectionIds();
            assertThat(replacement.size(), is(1));
            verify(lifecycle, times(2)).retireConnectionId(connection);
            assertThat(endpoint.findQuicConnectionFor(null, initial.asReadOnlyBuffer(), true),
                       is((QuicPacketReceiver) null));
            assertThat(endpoint.findQuicConnectionFor(null, replacement.getFirst().asReadOnlyBuffer(), true),
                       is(connection));
        }
    }

    @Test
    void freezeWaitsForRegistrationAndPreservesRouteSnapshot() throws Exception {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicConfig config = QuicConfig.create();
        QuicInstance instance = mock(QuicInstance.class);
        when(instance.executor()).thenReturn(Runnable::run);
        when(instance.isClient()).thenReturn(false);
        QuicEndpoint endpoint = QuicEndpoint.QuicEndpointFactory.create()
                .createVirtualThreadedEndpoint(instance,
                                               QuicRuntimeConfig.create(config),
                                               "local-connection-id-test",
                                               new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                               new QuicTimerQueue(() -> {
                                               }, () -> "quic.test.local-connection-id.timer"));
        QuicConnectionId initial = PeerConnectionId.create(new byte[] {1});
        when(connection.endpoint()).thenReturn(endpoint);

        CountDownLatch registrationEntered = new CountDownLatch(1);
        CountDownLatch freezeStarted = new CountDownLatch(1);
        CountDownLatch allowRegistration = new CountDownLatch(1);
        QuicEndpointRouteLifecycle lifecycle = new QuicEndpointRouteLifecycle(connection);
        assertThat(lifecycle.register(connection, 1), is(true));
        when(connection.routeLifecycle()).thenAnswer(invocation -> {
            registrationEntered.countDown();
            assertThat(allowRegistration.await(5, TimeUnit.SECONDS), is(true));
            return lifecycle;
        });

        LocalConnIdManager manager = new LocalConnIdManager(connection, initial);
        try (endpoint; ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<QuicFrame> registration = executor.submit(() -> manager.nextFrame(64));
            assertThat(registrationEntered.await(5, TimeUnit.SECONDS), is(true));
            Future<?> freeze = executor.submit(() -> {
                freezeStarted.countDown();
                manager.freezeConnectionIds();
            });
            assertThat(freezeStarted.await(5, TimeUnit.SECONDS), is(true));

            allowRegistration.countDown();
            assertThat(registration.get(5, TimeUnit.SECONDS), is(notNullValue()));
            freeze.get(5, TimeUnit.SECONDS);

            List<QuicConnectionId> frozen = manager.connectionIds();
            QuicConnectionId generated = frozen.get(1);
            assertThat(frozen, contains(initial, generated));
            manager.handleRetireConnectionIdFrame(initial,
                                                  QuicPacket.PacketType.ONERTT,
                                                  RetireConnectionIDFrame.create(1));
            assertThat(manager.connectionIds(), contains(initial, generated));
            assertThat(manager.nextFrame(64), is((QuicFrame) null));
            verify(connection, times(2)).routeLifecycle();
        }
    }
}
