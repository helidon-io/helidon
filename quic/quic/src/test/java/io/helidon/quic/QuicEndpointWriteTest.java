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
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import io.helidon.quic.QuicEndpoint.QuicDatagram;
import io.helidon.quic.QuicPathManager.SendPermit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuicEndpointWriteTest {
    @Test
    void closedEndpointSkipsStatelessDatagramWithoutConsumingIt() {
        QuicEndpoint endpoint = endpoint(false);
        ByteBuffer datagram = ByteBuffer.wrap(new byte[] {1, 2, 3});
        endpoint.close();

        assertThat(endpoint.sendStatelessDatagram(new InetSocketAddress(InetAddress.getLoopbackAddress(), 9),
                                                  datagram),
                   is(-1));
        assertThat(datagram.position(), is(0));
    }

    @Test
    void rejectsInvalidStatelessResetTokenLength() {
        InetSocketAddress peer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 4433);

        assertThrows(IllegalArgumentException.class,
                     () -> QuicPacketReceiver.PeerResetToken.create(new byte[15], peer));
    }

    @Test
    void closeFromSentCallbackDoesNotDropInFlightDatagram() {
        QuicEndpoint endpoint = endpoint(true);
        QuicPacketReceiver receiver = mock(QuicPacketReceiver.class);
        doAnswer(invocation -> {
            endpoint.close();
            return null;
        }).when(receiver).datagramSent(any(QuicDatagram.class));

        endpoint.pushDatagram(receiver,
                              new InetSocketAddress(InetAddress.getLoopbackAddress(), 9),
                              ByteBuffer.wrap(new byte[] {1}));

        verify(receiver).datagramSent(any(QuicDatagram.class));
        verify(receiver, never()).datagramDropped(any(QuicDatagram.class));
    }

    @Test
    void queuedUncheckedSendFailureDiscardsDatagram() {
        QuicEndpoint endpoint = endpoint(true);
        QuicPacketReceiver receiver = mock(QuicPacketReceiver.class);

        endpoint.pushDatagram(receiver,
                              InetSocketAddress.createUnresolved("unresolved.invalid", 9),
                              ByteBuffer.wrap(new byte[] {1}));

        verify(receiver).datagramDiscarded(any(QuicDatagram.class));
        verify(receiver, never()).datagramDropped(any(QuicDatagram.class));
    }

    @Test
    void directUncheckedSendFailureDiscardsDatagram() {
        QuicEndpoint endpoint = endpoint(false);
        QuicPacketReceiver receiver = mock(QuicPacketReceiver.class);

        try {
            assertThrows(UnresolvedAddressException.class,
                         () -> endpoint.pushDatagram(receiver,
                                                     InetSocketAddress.createUnresolved("unresolved.invalid", 9),
                                                     ByteBuffer.wrap(new byte[] {1})));
            verify(receiver).datagramDiscarded(any(QuicDatagram.class));
            verify(receiver, never()).datagramDropped(any(QuicDatagram.class));
        } finally {
            endpoint.close();
        }
    }

    @Test
    void sentCallbackFailureDoesNotDiscardCommittedDatagram() {
        QuicEndpoint endpoint = endpoint(false);
        QuicPacketReceiver receiver = mock(QuicPacketReceiver.class);
        SendPermit permit = mock(SendPermit.class);
        when(permit.size()).thenReturn(1);
        doThrow(new IllegalStateException("callback failure"))
                .when(receiver)
                .datagramSent(any(QuicDatagram.class));

        try {
            assertThrows(IllegalStateException.class,
                         () -> endpoint.pushDatagram(receiver,
                                                     new InetSocketAddress(InetAddress.getLoopbackAddress(), 9),
                                                     ByteBuffer.wrap(new byte[] {1}),
                                                     permit));
            verify(permit).commit();
            verify(permit, never()).release();
            verify(receiver, never()).datagramDiscarded(any(QuicDatagram.class));
            verify(receiver, never()).datagramDropped(any(QuicDatagram.class));
        } finally {
            endpoint.close();
        }
    }

    @Test
    void rejectedSchedulingAbortsAndDrainsQueuedDatagrams() {
        RejectedExecutionException rejection = new RejectedExecutionException("rejected");
        QuicEndpoint endpoint = endpoint(true, command -> {
            throw rejection;
        });
        QuicPacketReceiver receiver = mock(QuicPacketReceiver.class);
        SendPermit permit = mock(SendPermit.class);
        QuicPacketReceiver laterReceiver = mock(QuicPacketReceiver.class);
        SendPermit laterPermit = mock(SendPermit.class);

        try {
            assertThrows(RejectedExecutionException.class,
                         () -> endpoint.pushDatagram(receiver,
                                                     new InetSocketAddress(InetAddress.getLoopbackAddress(), 9),
                                                     ByteBuffer.wrap(new byte[] {1}),
                                                     permit));
            endpoint.pushDatagram(laterReceiver,
                                  new InetSocketAddress(InetAddress.getLoopbackAddress(), 9),
                                  ByteBuffer.wrap(new byte[] {2}),
                                  laterPermit);
            verify(permit).release();
            verify(receiver).datagramDropped(any(QuicDatagram.class));
            verify(receiver, never()).datagramSent(any(QuicDatagram.class));
            verify(receiver, never()).datagramDiscarded(any(QuicDatagram.class));
            verify(laterPermit).release();
            verify(laterReceiver).datagramDropped(any(QuicDatagram.class));
        } finally {
            endpoint.close();
        }
    }

    private static QuicEndpoint endpoint(boolean sendAsync) {
        return endpoint(sendAsync, Runnable::run);
    }

    private static QuicEndpoint endpoint(boolean sendAsync, Executor executor) {
        QuicConfig userConfig = QuicConfig.create();
        QuicRuntimeConfig defaults = QuicRuntimeConfig.create(userConfig);
        QuicRuntimeConfig.Endpoint defaultEndpoint = defaults.endpoint();
        QuicRuntimeConfig runtimeConfig = new QuicRuntimeConfig(
                userConfig,
                new QuicRuntimeConfig.Endpoint(defaultEndpoint.channelType(),
                                               defaultEndpoint.selectorThreading(),
                                               defaultEndpoint.pollerUsePlatformThreads(),
                                               defaultEndpoint.maxEndpoints(),
                                               sendAsync,
                                               defaultEndpoint.maxBufferedHigh(),
                                               defaultEndpoint.maxBufferedLow(),
                                               defaultEndpoint.useDirectBufferPool(),
                                               defaultEndpoint.defaultDatagramSize()),
                defaults.recovery(),
                defaults.transportParameters(),
                defaults.confidentialityLimits());
        QuicInstance instance = mock(QuicInstance.class);
        when(instance.quicConfig()).thenReturn(userConfig);
        when(instance.executor()).thenReturn(executor);
        when(instance.isClient()).thenReturn(true);
        when(instance.instanceId()).thenReturn("write-test");
        return QuicEndpoint.QuicEndpointFactory.create()
                .createVirtualThreadedEndpoint(instance,
                                               runtimeConfig,
                                               "write-test",
                                               new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                               new QuicTimerQueue(() -> {
                                               }, () -> "write-test-timer"));
    }
}
