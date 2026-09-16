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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.helidon.quic.frame.NewConnectionIDFrame;
import io.helidon.quic.frame.RetireConnectionIDFrame;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PeerConnIdManagerTest {
    private static final byte[] INITIAL_CONNECTION_ID = new byte[] {0x11, 0x22, 0x33, 0x44};
    private static final InetSocketAddress LOCAL_ADDRESS =
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 4433);
    private static final InetSocketAddress PEER_ADDRESS =
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 4434);

    @Test
    void resetTokenCleanupIncludesRegisteredButNotUnusedConnectionIds() throws Exception {
        try (TestContext context = TestContext.create()) {
            byte[] handshakeToken = new byte[16];
            Arrays.fill(handshakeToken, (byte) 1);
            byte[] unusedToken = new byte[16];
            Arrays.fill(unusedToken, (byte) 2);
            context.manager.handshakeStatelessResetToken(handshakeToken);
            context.manager.handleNewConnectionIdFrame(NewConnectionIDFrame.create(1,
                                                                                    0,
                                                                                    ByteBuffer.wrap(new byte[] {0x55}),
                                                                                    ByteBuffer.wrap(unusedToken)));

            assertThat(context.manager.activeResetTokens().stream()
                               .anyMatch(token -> Arrays.equals(token.token(), handshakeToken)), is(true));
            assertThat(context.manager.activeResetTokens().stream()
                               .anyMatch(token -> Arrays.equals(token.token(), unusedToken)), is(false));
        }
    }

    @Test
    void retirePriorToWaitsForStartedConnectionIdUse() throws Exception {
        try (TestContext context = TestContext.create()) {
            byte[] handshakeToken = new byte[16];
            Arrays.fill(handshakeToken, (byte) 1);
            context.manager.handshakeStatelessResetToken(handshakeToken);
            QuicPathManager.SendPermit startedPermit = context.pathManager.reserve(1).orElseThrow();
            PeerConnIdManager.PathCidBinding startedBinding = context.pathManager.cidBinding(startedPermit).orElseThrow();
            context.manager.handleNewConnectionIdFrame(NewConnectionIDFrame.create(1,
                                                                                    1,
                                                                                    ByteBuffer.wrap(new byte[] {0x55}),
                                                                                    ByteBuffer.wrap(new byte[16])));
            QuicPathManager.SendPermit replacementPermit = context.pathManager.reserve(1).orElseThrow();
            PeerConnIdManager.PathCidBinding replacement = context.pathManager.cidBinding(replacementPermit).orElseThrow();

            assertThat(startedBinding.sequence(), is(0L));
            assertThat(replacement.sequence(), is(1L));
            assertThat(context.manager.nextFrame(64, replacement.sequence()), is((Object) null));
            assertThat(context.manager.activeResetTokens().stream()
                               .anyMatch(token -> Arrays.equals(token.token(), handshakeToken)), is(true));

            startedPermit.release();
            replacementPermit.release();
        }
    }

    @Test
    void duplicateConnectionIdAppliesIncreasedRetirePriorToOnce() throws Exception {
        try (TestContext context = TestContext.create()) {
            QuicPathManager.SendPermit initialPermit = context.pathManager.reserve(1).orElseThrow();
            assertThat(context.pathManager.cidBinding(initialPermit).orElseThrow().sequence(), is(0L));
            initialPermit.release();
            ByteBuffer connectionId = ByteBuffer.wrap(new byte[] {0x55});
            ByteBuffer resetToken = ByteBuffer.wrap(new byte[16]);
            context.manager.handleNewConnectionIdFrame(NewConnectionIDFrame.create(1,
                                                                                    0,
                                                                                    connectionId,
                                                                                    resetToken));
            context.manager.handleNewConnectionIdFrame(NewConnectionIDFrame.create(1,
                                                                                    1,
                                                                                    connectionId.rewind(),
                                                                                    resetToken.rewind()));
            QuicPathManager.SendPermit replacementPermit = context.pathManager.reserve(1).orElseThrow();
            PeerConnIdManager.PathCidBinding replacement = context.pathManager.cidBinding(replacementPermit).orElseThrow();

            RetireConnectionIDFrame retirement =
                    (RetireConnectionIDFrame) context.manager.nextFrame(64, replacement.sequence());

            assertThat(replacement.sequence(), is(1L));
            assertThat(retirement.sequenceNumber(), is(0L));
            assertThat(context.manager.nextFrame(64, replacement.sequence()), is((Object) null));
            replacementPermit.release();
        }
    }

    @Test
    void completedConnectionIdRetirementAllowsNextLeaseAndIgnoresRetiredDuplicates() throws Exception {
        try (TestContext context = TestContext.create()) {
            QuicPathManager.SendPermit initialPermit = context.pathManager.reserve(1).orElseThrow();
            PeerConnIdManager.PathCidBinding binding = context.pathManager.cidBinding(initialPermit).orElseThrow();
            initialPermit.release();

            assertThat(binding.sequence(), is(0L));
            for (long replacementSequence = 1; replacementSequence <= 32; replacementSequence++) {
                context.manager.handleNewConnectionIdFrame(NewConnectionIDFrame.create(
                        replacementSequence,
                        0,
                        ByteBuffer.wrap(new byte[] {(byte) (replacementSequence + 1)}),
                        ByteBuffer.wrap(new byte[16])));
                context.manager.retirePathBinding(binding);

                RetireConnectionIDFrame retirement =
                        (RetireConnectionIDFrame) context.manager.nextFrame(64, replacementSequence);
                assertThat(retirement.sequenceNumber(), is(replacementSequence - 1));

                if (replacementSequence == 1) {
                    context.manager.handleNewConnectionIdFrame(NewConnectionIDFrame.create(
                            0,
                            0,
                            ByteBuffer.wrap(INITIAL_CONNECTION_ID),
                            ByteBuffer.wrap(new byte[16])));
                }

                binding = context.manager.acquirePathBinding(new InetSocketAddress(InetAddress.getLoopbackAddress(),
                                                                                    50000 + (int) replacementSequence))
                        .orElseThrow();
                assertThat(binding.sequence(), is(replacementSequence));
            }

            context.manager.retirePathBinding(binding);
            RetireConnectionIDFrame finalRetirement = (RetireConnectionIDFrame) context.manager.nextFrame(64, -1);
            assertThat(finalRetirement.sequenceNumber(), is(32L));
        }
    }

    @Test
    void freezeWaitsForAssociationAndPreservesRouteSnapshot() throws Exception {
        try (TestContext context = TestContext.create()) {
            CountDownLatch associationEntered = new CountDownLatch(1);
            CountDownLatch freezeStarted = new CountDownLatch(1);
            CountDownLatch allowAssociation = new CountDownLatch(1);
            QuicEndpointRouteLifecycle lifecycle = new QuicEndpointRouteLifecycle(context.connection);
            assertThat(lifecycle.register(context.connection, 1), is(true));
            when(context.connection.routeLifecycle()).thenAnswer(invocation -> {
                associationEntered.countDown();
                assertThat(allowAssociation.await(5, TimeUnit.SECONDS), is(true));
                return lifecycle;
            });

            byte[] firstToken = new byte[16];
            Arrays.fill(firstToken, (byte) 1);
            List<QuicPacketReceiver.PeerResetToken> frozen;
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<?> association = executor.submit(() -> context.manager.handshakeStatelessResetToken(firstToken));
                assertThat(associationEntered.await(5, TimeUnit.SECONDS), is(true));
                Future<List<QuicPacketReceiver.PeerResetToken>> freeze = executor.submit(() -> {
                    freezeStarted.countDown();
                    return context.manager.freezeResetTokenRoutes();
                });
                assertThat(freezeStarted.await(5, TimeUnit.SECONDS), is(true));

                allowAssociation.countDown();
                association.get(5, TimeUnit.SECONDS);
                frozen = freeze.get(5, TimeUnit.SECONDS);
            }

            assertThat(frozen.stream().anyMatch(token -> Arrays.equals(token.token(), firstToken)), is(true));
            byte[] laterToken = new byte[16];
            Arrays.fill(laterToken, (byte) 2);
            context.manager.handshakeStatelessResetToken(laterToken);

            QuicPathManager.SendPermit permit = context.pathManager.reserve(1).orElseThrow();
            PeerConnIdManager.PathCidBinding binding = context.pathManager.cidBinding(permit).orElseThrow();
            permit.release();
            context.manager.retirePathBinding(binding);
            context.manager.nextFrame(64, -1);

            assertThat(context.manager.freezeResetTokenRoutes(), sameInstance(frozen));
            assertThat(frozen.stream().anyMatch(token -> Arrays.equals(token.token(), firstToken)), is(true));
            verify(context.connection, times(1)).routeLifecycle();
        }
    }

    private record TestContext(QuicConnectionImpl connection,
                               PeerConnIdManager manager,
                               QuicPathManager pathManager,
                               QuicEndpoint endpoint) implements AutoCloseable {
        @Override
        public void close() {
            endpoint.close();
        }

        private static TestContext create() throws Exception {
            QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
            QuicConfig config = QuicConfig.builder()
                    .availableVersions(List.of(QuicVersion.QUIC_V1))
                    .maxUniStreams(4)
                    .buildPrototype();
            QuicInstance instance = mock(QuicInstance.class);
            when(instance.executor()).thenReturn(Runnable::run);
            when(instance.isClient()).thenReturn(true);
            QuicEndpoint endpoint = QuicEndpoint.QuicEndpointFactory.create()
                    .createVirtualThreadedEndpoint(instance,
                                                   QuicRuntimeConfig.create(config),
                                                   "peer-connection-id-test",
                                                   new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                                   new QuicTimerQueue(() -> {
                                                   }, () -> "quic.test.peer-connection-id.timer"));
            when(connection.isClientConnection()).thenReturn(true);
            when(connection.localActiveConnectionIdLimit()).thenReturn(64L);
            when(connection.localConnectionId()).thenReturn(Optional.of(PeerConnectionId.create(new byte[] {1})));
            when(connection.quicConfig()).thenReturn(config);
            when(connection.endpoint()).thenReturn(endpoint);
            PeerConnIdManager manager = new PeerConnIdManager(connection);
            PeerConnectionId initialConnectionId = PeerConnectionId.create(INITIAL_CONNECTION_ID);
            manager.originalServerConnId(initialConnectionId);
            manager.finalizeHandshakePeerConnId(initialConnectionId);
            QuicPathManager pathManager = new QuicPathManager(true,
                                                              LOCAL_ADDRESS,
                                                              PEER_ADDRESS,
                                                              1200,
                                                              manager);
            pathManager.bindInitialPath();
            doAnswer(invocation -> new QuicConnectionImpl.EndpointRoutes(List.of(), manager.freezeResetTokenRoutes()))
                    .when(connection)
                    .freezeEndpointRoutes();
            return new TestContext(connection, manager, pathManager, endpoint);
        }
    }
}
