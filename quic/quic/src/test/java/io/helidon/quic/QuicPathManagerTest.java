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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.quic.frame.PathChallengeFrame;
import io.helidon.quic.frame.PathResponseFrame;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class QuicPathManagerTest {
    private static final InetSocketAddress LOCAL = address(4433);
    private static final InetSocketAddress INITIAL_PEER = address(50000);
    private static final InetSocketAddress REBOUND_PEER = address(50001);
    private static final InetSocketAddress SECOND_REBOUND_PEER = address(50002);
    private static final InetSocketAddress PROBING_PEER = address(50003);

    @Test
    void enforcesExactServerAmplificationBudget() {
        QuicPathManager manager = serverManager();
        manager.receive(INITIAL_PEER, 400);

        QuicPathManager.SendPermit first = manager.reserve(1200).orElseThrow();
        assertThat(first.size(), is(1200));
        assertThat(manager.reserve(1).isEmpty(), is(true));

        first.resize(600);
        first.commit();
        QuicPathManager.SendPermit second = manager.reserve(601).orElseThrow();
        assertThat(second.size(), is(600));
        second.release();

        QuicPathManager.SendPermit replacement = manager.reserve(600).orElseThrow();
        replacement.release();
        replacement.release();
    }

    @Test
    void validatedReservationRejectsAmplificationCreditUntilAddressValidation() {
        QuicPathManager manager = serverManager();
        manager.receive(INITIAL_PEER, 10_000);

        assertThat(manager.reserveValidated(10_000).isEmpty(), is(true));

        manager.addressValidated(INITIAL_PEER);
        QuicPathManager.SendPermit permit = manager.reserveValidated(10_000).orElseThrow();
        assertThat(permit.size(), is(10_000));
        permit.release();
    }

    @Test
    void validatedReservationRejectsClosedPathManager() {
        QuicPathManager manager = serverManager();
        manager.addressValidated(INITIAL_PEER);
        manager.close();

        assertThat(manager.reserveValidated(1).isEmpty(), is(true));
    }

    @Test
    void defersUnknownPathStateUntilPacketAuthentication() {
        QuicPathManager manager = serverManager();
        QuicPathManager.ReceiveContext context = manager.receive(REBOUND_PEER, 1000);

        assertThat(context.credited(), is(false));
        assertThat(context.amplificationBudgetIncreased(), is(false));
        assertThat(manager.reserve(REBOUND_PEER, 1).isEmpty(), is(true));

        QuicPathManager.ReceiveResult result = manager.authenticated(context, 7, false, 100);
        assertThat(result.accepted(), is(true));
        assertThat(result.pathChanged(), is(false));
        assertThat(context.credited(), is(true));
        assertThat(context.amplificationBudgetIncreased(), is(true));
        assertThat(manager.reserve(REBOUND_PEER, 3000).orElseThrow().size(), is(3000));
    }

    @Test
    void validatedCurrentPathDoesNotReportNewAmplificationBudget() {
        QuicPathManager manager = serverManager();
        QuicPathManager.ReceiveContext unvalidated = manager.receive(INITIAL_PEER, 400);

        assertThat(unvalidated.credited(), is(true));
        assertThat(unvalidated.amplificationBudgetIncreased(), is(true));

        manager.addressValidated(INITIAL_PEER);
        InetSocketAddress equivalentAddress = new InetSocketAddress(INITIAL_PEER.getAddress(), INITIAL_PEER.getPort());
        QuicPathManager.ReceiveContext validated = manager.receive(equivalentAddress, 400);

        assertThat(validated.credited(), is(true));
        assertThat(validated.amplificationBudgetIncreased(), is(false));
    }

    @Test
    void obtainsValidationTimeoutOnlyForAPathThatNeedsValidation() {
        QuicPathManager manager = serverManager();
        manager.addressValidated(INITIAL_PEER);
        AtomicInteger timeoutRequests = new AtomicInteger();

        manager.authenticated(manager.receive(INITIAL_PEER, 1200),
                              1,
                              true,
                              () -> {
                                  timeoutRequests.incrementAndGet();
                                  return 100;
                              });
        assertThat(timeoutRequests.get(), is(0));

        manager.authenticated(manager.receive(REBOUND_PEER, 1200),
                              2,
                              false,
                              () -> {
                                  timeoutRequests.incrementAndGet();
                                  return 100;
                              });
        assertThat(timeoutRequests.get(), is(1));
    }

    @Test
    void initializesCallerOwnedReceiveContextWithoutReplacingIt() {
        QuicPathManager manager = serverManager();
        ExtendedReceiveContext context = new ExtendedReceiveContext(INITIAL_PEER, 1200);

        ExtendedReceiveContext received = manager.receive(context);

        assertThat(received, sameInstance(context));
        assertThat(received.credited(), is(true));
    }

    @Test
    void switchesOnlyOnHigherNonProbingPacketNumber() {
        QuicPathManager manager = serverManager();
        manager.addressValidated(INITIAL_PEER);
        long initialGeneration = manager.generation();
        QuicPathManager.ReceiveContext initial = manager.receive(INITIAL_PEER, 1200);
        manager.authenticated(initial, 9, true, 100);

        QuicPathManager.ReceiveContext rebound = manager.receive(REBOUND_PEER, 1200);
        QuicPathManager.ReceiveResult changed = manager.authenticated(rebound, 10, true, 100);
        assertThat(changed.pathChanged(), is(true));
        manager.pathChangeCompleted(changed.generation());
        assertThat(manager.peerAddress(), is(REBOUND_PEER));
        assertThat(manager.generation() > initialGeneration, is(true));

        QuicPathManager.ReceiveContext delayed = manager.receive(INITIAL_PEER, 1200);
        QuicPathManager.ReceiveResult unchanged = manager.authenticated(delayed, 9, true, 100);
        assertThat(unchanged.pathChanged(), is(false));
        assertThat(manager.peerAddress(), is(REBOUND_PEER));
    }

    @Test
    void migrationValidatesNewAndPreviouslyActivePaths() {
        QuicPathManager manager = serverManager();
        manager.addressValidated(INITIAL_PEER);

        QuicPathManager.ReceiveResult migration =
                manager.authenticated(manager.receive(REBOUND_PEER, 1200), 1, true, 100);
        manager.pathChangeCompleted(migration.generation());

        List<InetSocketAddress> destinations = new ArrayList<>();
        while (manager.hasPendingProbe()) {
            QuicPathManager.Probe probe = pollProbe(manager);
            if (probe.challenge()) {
                destinations.add(probe.destination());
            }
        }
        assertThat(destinations, contains(REBOUND_PEER, INITIAL_PEER));
    }

    @Test
    void previousPathResponseDoesNotSelectPreviousPath() {
        QuicPathManager manager = serverManager();
        manager.addressValidated(INITIAL_PEER);
        QuicPathManager.ReceiveResult migration =
                manager.authenticated(manager.receive(REBOUND_PEER, 1200), 1, true, 100);
        manager.pathChangeCompleted(migration.generation());
        QuicPathManager.Probe newPathChallenge = pollProbe(manager);
        QuicPathManager.Probe previousPathChallenge = pollProbe(manager);
        manager.probeSent(newPathChallenge, 1200, 10);
        manager.probeSent(previousPathChallenge, 1200, 10);

        manager.pathResponse(((PathChallengeFrame) previousPathChallenge.frame()).data(), 20, 100);

        assertThat(manager.peerAddress(), is(REBOUND_PEER));
        QuicPathManager.ReceiveResult returned =
                manager.authenticated(manager.receive(INITIAL_PEER, 1200), 2, true, 100);
        assertThat(returned.pathChanged(), is(true));
        assertThat(manager.peerAddress(), is(INITIAL_PEER));
    }

    @Test
    void activePathChallengeRequestsNonProbingResponse() {
        QuicPathManager manager = serverManager();
        QuicPathManager.ReceiveContext current = manager.receive(INITIAL_PEER, 1200);
        manager.pathChallenge(current, ByteBuffer.allocate(Long.BYTES).putLong(1L).flip());

        QuicPathManager.Probe currentResponse = pollProbe(manager);

        assertThat(currentResponse.ping(), is(true));

        QuicPathManager.ReceiveContext candidate = manager.receive(REBOUND_PEER, 1200);
        manager.authenticated(candidate, 1, false, 100);
        manager.pathChallenge(candidate, ByteBuffer.allocate(Long.BYTES).putLong(2L).flip());
        QuicPathManager.Probe candidateResponse = pollProbe(manager);
        assertThat(candidateResponse.destination(), is(REBOUND_PEER));
        assertThat(candidateResponse.ping(), is(false));
    }

    @Test
    void sendsPathResponseOnChallengeIngressPath() {
        QuicPathManager manager = serverManager();
        QuicPathManager.ReceiveContext rebound = manager.receive(REBOUND_PEER, 1200);
        manager.authenticated(rebound, 1, false, 100);
        ByteBuffer data = ByteBuffer.allocate(Long.BYTES).putLong(42).flip();

        manager.pathChallenge(rebound, data);

        QuicPathManager.Probe response = pollProbe(manager);
        assertThat(response.destination(), is(REBOUND_PEER));
        assertThat(response.frame(), instanceOf(PathResponseFrame.class));
        assertThat(((PathResponseFrame) response.frame()).data().getLong(), is(42L));
    }

    @Test
    void boundsQueuedPathResponses() {
        QuicPathManager manager = serverManager();
        QuicPathManager.ReceiveContext initial = manager.receive(INITIAL_PEER, 1200);
        for (long marker = 0; marker < 5; marker++) {
            manager.pathChallenge(initial, ByteBuffer.allocate(Long.BYTES).putLong(marker).flip());
        }

        List<Long> markers = new ArrayList<>();
        while (manager.hasPendingProbe()) {
            QuicPathManager.Probe probe = pollProbe(manager);
            markers.add(((PathResponseFrame) probe.frame()).data().getLong());
        }
        assertThat(markers, contains(2L, 3L, 4L));
    }

    @Test
    void matchingResponseValidatesChallengedPath() {
        QuicPathManager manager = serverManager();
        QuicPathManager.ReceiveContext rebound = manager.receive(REBOUND_PEER, 1200);
        manager.authenticated(rebound, 1, false, 100);
        QuicPathManager.Probe challenge = pollProbe(manager);

        assertThat(challenge.frame(), instanceOf(PathChallengeFrame.class));
        manager.probeSent(challenge, 1200, 10);
        ByteBuffer responseData = ((PathChallengeFrame) challenge.frame()).data();
        assertThat(manager.pathResponse(responseData, 20, 100), is(true));

        QuicPathManager.SendPermit permit = manager.reserve(REBOUND_PEER, 100_000).orElseThrow();
        assertThat(permit.size(), is(100_000));
        permit.release();
    }

    @Test
    void validationKeepsQueuedPathResponse() {
        QuicPathManager manager = serverManager();
        QuicPathManager.ReceiveContext rebound = manager.receive(REBOUND_PEER, 1200);
        manager.authenticated(rebound, 1, false, 100);
        QuicPathManager.Probe localChallenge = pollProbe(manager);
        manager.probeSent(localChallenge, 1200, 10);
        manager.pathChallenge(rebound, ByteBuffer.allocate(Long.BYTES).putLong(42L).flip());

        manager.pathResponse(((PathChallengeFrame) localChallenge.frame()).data(), 20, 100);

        QuicPathManager.Probe response = pollProbe(manager);
        assertThat(response.frame(), instanceOf(PathResponseFrame.class));
        assertThat(((PathResponseFrame) response.frame()).data().getLong(), is(42L));
    }

    @Test
    void pathResponseBeforeChallengePreservesAuthenticatedIngressPath() {
        QuicPathManager manager = serverManager();
        QuicPathManager.ReceiveContext challengedPath = manager.receive(REBOUND_PEER, 1200);
        manager.authenticated(challengedPath, 1, false, 100);
        QuicPathManager.Probe challenge = pollProbe(manager);
        manager.probeSent(challenge, 1200, 10);
        QuicPathManager.ReceiveContext ingressPath = manager.receive(SECOND_REBOUND_PEER, 1200);
        manager.authenticated(ingressPath, 2, false, 100);

        manager.pathResponse(((PathChallengeFrame) challenge.frame()).data(), 20, 100);
        manager.pathChallenge(ingressPath, ByteBuffer.allocate(Long.BYTES).putLong(42L).flip());

        QuicPathManager.Probe response = pollProbe(manager);
        assertThat(response.destination(), is(SECOND_REBOUND_PEER));
        assertThat(response.frame(), instanceOf(PathResponseFrame.class));
        assertThat(((PathResponseFrame) response.frame()).data().getLong(), is(42L));
    }

    @Test
    void queuedPathResponseRetainsAccountingAcrossCandidateReplacement() {
        QuicPathManager manager = serverManager();
        QuicPathManager.ReceiveContext firstCandidate = manager.receive(REBOUND_PEER, 400);
        manager.authenticated(firstCandidate, 1, false, 100);
        QuicPathManager.SendPermit exhausted = manager.reserve(REBOUND_PEER, 1200).orElseThrow();
        exhausted.commit();
        manager.pathChallenge(firstCandidate, ByteBuffer.allocate(Long.BYTES).putLong(42L).flip());
        QuicPathManager.ReceiveContext replacement = manager.receive(SECOND_REBOUND_PEER, 400);
        manager.authenticated(replacement, 2, false, 100);

        assertThat(manager.pollSendableResponse(1200).isEmpty(), is(true));

        QuicPathManager.ReceiveContext moreCredit = manager.receive(REBOUND_PEER, 400);
        manager.authenticated(moreCredit, 3, false, 100);
        QuicPathManager.ProbeSend response = manager.pollSendableResponse(1200).orElseThrow();
        assertThat(response.probe().destination(), is(REBOUND_PEER));
        assertThat(response.probe().frame(), instanceOf(PathResponseFrame.class));
        QuicPathManager.SendPermit responsePermit = response.permit();
        manager.probeSent(response.probe(), 1200, 10);
        responsePermit.commit();

        assertThat(responsePermit.destination(), is(REBOUND_PEER));
        assertThat(manager.reserve(REBOUND_PEER, 1).isEmpty(), is(true));
    }

    @Test
    void replacesResponseForRepeatedPathChallenge() {
        QuicPathManager manager = serverManager();
        QuicPathManager.ReceiveContext initial = manager.receive(INITIAL_PEER, 1200);
        ByteBuffer challenge = ByteBuffer.allocate(Long.BYTES).putLong(42L).flip();

        manager.pathChallenge(initial, challenge);
        manager.pathChallenge(initial, challenge.rewind());

        assertThat(((PathResponseFrame) pollProbe(manager).frame()).data().getLong(), is(42L));
        assertThat(((PathResponseFrame) pollProbe(manager).frame()).data().getLong(), is(42L));
    }

    @Test
    void retiresProbeWhenCandidateIsReplaced() {
        QuicPathManager manager = serverManager();
        QuicPathManager.ReceiveContext firstCandidate = manager.receive(REBOUND_PEER, 1200);
        manager.authenticated(firstCandidate, 1, false, 100);
        QuicPathManager.ReceiveContext replacement = manager.receive(SECOND_REBOUND_PEER, 1200);
        manager.authenticated(replacement, 2, false, 100);

        QuicPathManager.Probe remaining = pollProbe(manager);

        assertThat(remaining.destination(), is(SECOND_REBOUND_PEER));
        assertThat(manager.hasPendingProbe(), is(false));
    }

    @Test
    void validatesChallengedPathWhenResponseArrivesOnAnotherPath() {
        QuicPathManager manager = serverManager();
        QuicPathManager.ReceiveContext challenged = manager.receive(REBOUND_PEER, 1200);
        manager.authenticated(challenged, 1, false, 100);
        QuicPathManager.Probe challenge = pollProbe(manager);
        manager.probeSent(challenge, 1200, 10);
        QuicPathManager.ReceiveContext responsePath = manager.receive(SECOND_REBOUND_PEER, 1200);
        manager.authenticated(responsePath, 2, false, 100);

        assertThat(manager.pathResponse(((PathChallengeFrame) challenge.frame()).data(), 30, 100), is(true));
        assertThat(manager.reserve(REBOUND_PEER, 100_000).orElseThrow().size(), is(100_000));
    }

    @Test
    void blocksNewPathSendsUntilRecoveryStateIsReset() {
        QuicPathManager manager = serverManager();
        manager.addressValidated(INITIAL_PEER);
        QuicPathManager.ReceiveContext rebound = manager.receive(REBOUND_PEER, 1200);

        QuicPathManager.ReceiveResult changed = manager.authenticated(rebound, 1, true, 100);

        assertThat(manager.reserve(1).isEmpty(), is(true));
        manager.pathChangeCompleted(changed.generation());
        assertThat(manager.reserve(1).isPresent(), is(true));
    }

    @Test
    void validationTimeoutRevertsToLastValidatedPath() {
        QuicPathManager manager = serverManager();
        manager.addressValidated(INITIAL_PEER);
        QuicPathManager.ReceiveContext rebound = manager.receive(REBOUND_PEER, 1200);
        QuicPathManager.ReceiveResult migration = manager.authenticated(rebound, 10, true, 100);
        manager.pathChangeCompleted(migration.generation());
        QuicPathManager.Probe challenge = pollProbe(manager);
        manager.probeSent(challenge, 1200, 10);

        QuicPathManager.TimeoutResult result = manager.validationTimedOut(111);

        assertThat(result.pathChanged(), is(true));
        assertThat(manager.peerAddress(), is(INITIAL_PEER));
    }

    @Test
    void ignoresResponseToRetiredChallengeAfterFallback() {
        QuicPathManager manager = serverManager();
        manager.addressValidated(INITIAL_PEER);
        QuicPathManager.ReceiveResult changed =
                manager.authenticated(manager.receive(REBOUND_PEER, 1200), 10, true, 100);
        manager.pathChangeCompleted(changed.generation());
        QuicPathManager.Probe challenge = pollProbe(manager);
        manager.probeSent(challenge, 1200, 10);
        QuicPathManager.TimeoutResult fallback = manager.validationTimedOut(111);
        manager.pathChangeCompleted(fallback.generation());

        boolean matched = manager.pathResponse(((PathChallengeFrame) challenge.frame()).data(), 120, 100);

        assertThat(matched, is(true));
        assertThat(manager.peerAddress(), is(INITIAL_PEER));
    }

    @Test
    void rapidRebindingRetainsLastValidatedFallback() {
        QuicPathManager manager = serverManager();
        manager.addressValidated(INITIAL_PEER);
        QuicPathManager.ReceiveResult firstSwitch =
                manager.authenticated(manager.receive(REBOUND_PEER, 1200), 10, true, 100);
        manager.pathChangeCompleted(firstSwitch.generation());
        QuicPathManager.ReceiveResult secondSwitch =
                manager.authenticated(manager.receive(SECOND_REBOUND_PEER, 1200), 11, true, 100);
        manager.pathChangeCompleted(secondSwitch.generation());
        QuicPathManager.Probe challenge = pollProbe(manager);
        while (!challenge.destination().equals(SECOND_REBOUND_PEER)) {
            manager.requeue(challenge);
            challenge = pollProbe(manager);
        }
        manager.probeSent(challenge, 1200, 20);

        QuicPathManager.TimeoutResult result = manager.validationTimedOut(121);

        assertThat(result.pathChanged(), is(true));
        assertThat(manager.peerAddress(), is(INITIAL_PEER));
    }

    @Test
    void validatedPendingPreviousReplacesOlderFallback() {
        QuicPathManager manager = serverManager();
        manager.addressValidated(INITIAL_PEER);
        QuicPathManager.ReceiveResult firstSwitch =
                manager.authenticated(manager.receive(REBOUND_PEER, 1200), 10, true, 100);
        manager.pathChangeCompleted(firstSwitch.generation());
        QuicPathManager.ReceiveResult secondSwitch =
                manager.authenticated(manager.receive(SECOND_REBOUND_PEER, 1200), 11, true, 100);
        manager.pathChangeCompleted(secondSwitch.generation());
        manager.authenticated(manager.receive(PROBING_PEER, 1200), 12, false, 100);
        QuicPathManager.Probe pendingPreviousChallenge = null;
        QuicPathManager.Probe currentChallenge = null;
        while (manager.hasPendingProbe()) {
            QuicPathManager.Probe probe = pollProbe(manager);
            if (probe.destination().equals(REBOUND_PEER)) {
                pendingPreviousChallenge = probe;
            } else if (probe.destination().equals(SECOND_REBOUND_PEER)) {
                currentChallenge = probe;
            }
        }
        manager.probeSent(pendingPreviousChallenge, 1200, 10);
        manager.pathResponse(((PathChallengeFrame) pendingPreviousChallenge.frame()).data(), 20, 100);
        manager.probeSent(currentChallenge, 1200, 30);

        QuicPathManager.TimeoutResult result = manager.validationTimedOut(131);

        assertThat(result.pathChanged(), is(true));
        assertThat(manager.peerAddress(), is(REBOUND_PEER));
        assertThat(manager.reserve(1).isEmpty(), is(true));
        manager.pathChangeCompleted(result.generation());
        QuicPathManager.SendPermit permit = manager.reserve(1).orElseThrow();
        assertThat(permit.destination(), is(REBOUND_PEER));
        permit.release();
    }

    @Test
    void currentValidationDoesNotCancelPendingPreviousValidation() {
        QuicPathManager manager = serverManager();
        manager.addressValidated(INITIAL_PEER);
        QuicPathManager.ReceiveResult firstSwitch =
                manager.authenticated(manager.receive(REBOUND_PEER, 1200), 10, true, 100);
        manager.pathChangeCompleted(firstSwitch.generation());
        QuicPathManager.ReceiveResult secondSwitch =
                manager.authenticated(manager.receive(SECOND_REBOUND_PEER, 1200), 11, true, 100);
        manager.pathChangeCompleted(secondSwitch.generation());
        QuicPathManager.Probe pendingPreviousChallenge = null;
        QuicPathManager.Probe currentChallenge = null;
        while (manager.hasPendingProbe()) {
            QuicPathManager.Probe probe = pollProbe(manager);
            if (probe.destination().equals(REBOUND_PEER)) {
                pendingPreviousChallenge = probe;
            } else if (probe.destination().equals(SECOND_REBOUND_PEER)) {
                currentChallenge = probe;
            }
        }
        manager.probeSent(currentChallenge, 1200, 10);
        manager.pathResponse(((PathChallengeFrame) currentChallenge.frame()).data(), 20, 100);
        manager.probeSent(pendingPreviousChallenge, 1200, 30);

        boolean matched = manager.pathResponse(((PathChallengeFrame) pendingPreviousChallenge.frame()).data(), 40, 100);

        assertThat(matched, is(true));
        assertThat(manager.peerAddress(), is(SECOND_REBOUND_PEER));
    }

    @Test
    void reportsFailureWhenNoValidatedFallbackExists() {
        QuicPathManager manager = serverManager();
        QuicPathManager.ReceiveResult changed =
                manager.authenticated(manager.receive(REBOUND_PEER, 1200), 10, true, 100);
        manager.pathChangeCompleted(changed.generation());
        QuicPathManager.Probe challenge = pollProbe(manager);
        manager.probeSent(challenge, 1200, 10);

        QuicPathManager.TimeoutResult result = manager.validationTimedOut(111);

        assertThat(result.pathFailed(), is(true));
    }

    @Test
    void retriesValidationWithFreshChallengeData() {
        QuicPathManager manager = serverManager();
        QuicPathManager.ReceiveContext rebound = manager.receive(REBOUND_PEER, 1200);
        manager.authenticated(rebound, 1, false, 90);
        QuicPathManager.Probe first = pollProbe(manager);
        manager.probeSent(first, 1200, 10);

        QuicPathManager.TimeoutResult result = manager.validationTimedOut(40);
        QuicPathManager.Probe retry = pollProbe(manager);
        manager.probeSent(retry, 1200, 40);

        assertThat(result.pathChanged(), is(false));
        assertThat(retry.challenge(), is(true));
        assertThat(retry.token() == first.token(), is(false));
        assertThat(manager.nextValidationDeadlineNanos().orElseThrow(), is(100L));
    }

    @Test
    void startsValidationTimeoutWhenChallengeReachesTransport() {
        QuicPathManager manager = serverManager();
        QuicPathManager.ReceiveContext rebound = manager.receive(REBOUND_PEER, 1200);
        manager.authenticated(rebound, 1, false, 90);

        assertThat(manager.nextValidationDeadlineNanos().isEmpty(), is(true));
        assertThat(manager.validationTimedOut(1_000).pathChanged(), is(false));
        QuicPathManager.Probe challenge = pollProbe(manager);
        manager.probeSent(challenge, 1200, 10);

        assertThat(manager.nextValidationDeadlineNanos().orElseThrow(), is(40L));
    }

    @Test
    void clientRejectsUnknownServerAddressAndIsSendUnrestricted() {
        QuicPathManager manager = new QuicPathManager(true, LOCAL, INITIAL_PEER, 1200);

        assertThat(manager.accepts(INITIAL_PEER), is(true));
        assertThat(manager.accepts(REBOUND_PEER), is(false));
        assertThat(manager.reserve(Integer.MAX_VALUE).orElseThrow().size(), is(Integer.MAX_VALUE));
    }

    @Test
    void closingPathIsDetachedAndPreservesServerAmplificationAccounting() {
        PeerConnIdManager connectionIdManager = mock(PeerConnIdManager.class);
        QuicPathManager manager = new QuicPathManager(false, LOCAL, INITIAL_PEER, 1200, connectionIdManager);
        manager.receive(INITIAL_PEER, 400);
        QuicPathManager.SendPermit liveReservation = manager.reserve(200).orElseThrow();
        QuicPathManager.ClosingPath closingPath = manager.closingPath();
        manager.close();
        clearInvocations(connectionIdManager);

        QuicPathManager.SendPermit closingReservation = closingPath.reserve(1000).orElseThrow();
        assertThat(closingPath.reserve(1).isEmpty(), is(true));
        assertThat(closingReservation.binding(), is((PeerConnIdManager.PathCidBinding) null));
        assertThat(manager.cidBinding(closingReservation).isEmpty(), is(true));
        closingReservation.resize(600);
        closingReservation.commit();
        closingReservation.release();

        assertThat(closingPath.reserve(401).isEmpty(), is(true));
        QuicPathManager.SendPermit remaining = closingPath.reserve(400).orElseThrow();
        remaining.release();
        remaining.commit();
        closingPath.received(100);
        QuicPathManager.SendPermit increased = closingPath.reserve(700).orElseThrow();
        increased.release();

        verifyNoMoreInteractions(connectionIdManager);
        liveReservation.release();
    }

    @Test
    void clientClosingPathRemainsUnrestrictedAfterManagerClose() {
        QuicPathManager manager = new QuicPathManager(true, LOCAL, INITIAL_PEER, 1200);
        QuicPathManager.ClosingPath closingPath = manager.closingPath();
        manager.close();

        QuicPathManager.SendPermit permit = closingPath.reserve(Integer.MAX_VALUE).orElseThrow();

        assertThat(permit.size(), is(Integer.MAX_VALUE));
        permit.release();
    }

    private static QuicPathManager serverManager() {
        PeerConnIdManager connectionIdManager = mock(PeerConnIdManager.class);
        PeerConnIdManager.PathCidBinding binding = mock(PeerConnIdManager.PathCidBinding.class);
        when(binding.valid()).thenReturn(true);
        when(connectionIdManager.acquirePathBinding(any())).thenReturn(Optional.of(binding));
        when(connectionIdManager.acquirePathUse(any())).thenReturn(true);
        return new QuicPathManager(false, LOCAL, INITIAL_PEER, 1200, connectionIdManager);
    }

    private static QuicPathManager.Probe pollProbe(QuicPathManager manager) {
        QuicPathManager.ProbeSend send = manager.pollSendableProbe(1200).orElseThrow();
        send.permit().release();
        return send.probe();
    }

    private static InetSocketAddress address(int port) {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
    }

    private static final class ExtendedReceiveContext extends QuicPathManager.ReceiveContext {
        private ExtendedReceiveContext(InetSocketAddress source, int datagramBytes) {
            super(source, datagramBytes);
        }
    }
}
