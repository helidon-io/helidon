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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

import io.helidon.quic.frame.PathChallengeFrame;
import io.helidon.quic.frame.PathResponseFrame;
import io.helidon.quic.frame.QuicFrame;

/**
 * Connection-owned network-path state.
 */
final class QuicPathManager implements AutoCloseable {
    private static final int MINIMUM_DATAGRAM_SIZE = 1200;
    private static final int MAX_IPV6_DATAGRAM_SIZE = 65527;
    private static final int MAX_IPV4_DATAGRAM_SIZE = 65507;
    private static final int MAX_PENDING_PROBES = 8;
    private static final int MAX_PENDING_RESPONSES = 3;
    private static final int MAX_CHALLENGES = 9;
    private static final int MAX_RETIRED_CHALLENGES = 32;
    private static final int MAX_RETIRED_RESPONSE_PATHS = MAX_PENDING_RESPONSES + 1;
    private static final int MAX_RETIRED_VALIDATION_PATHS = 2;

    private final boolean client;
    private final InetSocketAddress localAddress;
    private final int initialDatagramSize;
    private final SecureRandom random;
    private final PeerConnIdManager peerConnIdManager;
    private final ReentrantLock lock = new ReentrantLock();
    private final Deque<Probe> probes = new ArrayDeque<>();
    private final ConcurrentLinkedQueue<Long> retiredPathGenerations = new ConcurrentLinkedQueue<>();
    private final Map<Long, Challenge> challenges = new LinkedHashMap<>();
    private final Map<Long, Boolean> retiredChallenges = new LinkedHashMap<>();
    private final Map<PathKey, Path> retiredResponsePaths = new LinkedHashMap<>();
    private final Map<PathKey, Path> retiredValidationPaths = new LinkedHashMap<>();

    private final AtomicLong highestPathSelectionPacket = new AtomicLong(-1);
    private volatile Path current;
    private Path previous;
    private Path pendingPrevious;
    private Path candidate;
    private long nextGeneration;
    private volatile long validationDeadlineNanos = Long.MAX_VALUE;
    private volatile boolean closed;

    QuicPathManager(boolean client,
                    InetSocketAddress localAddress,
                    InetSocketAddress peerAddress,
                    int initialDatagramSize) {
        this(client, localAddress, peerAddress, initialDatagramSize, new SecureRandom(), null);
    }

    QuicPathManager(boolean client,
                    InetSocketAddress localAddress,
                    InetSocketAddress peerAddress,
                    int initialDatagramSize,
                    PeerConnIdManager peerConnIdManager) {
        this(client, localAddress, peerAddress, initialDatagramSize, new SecureRandom(), peerConnIdManager);
    }

    private QuicPathManager(boolean client,
                            InetSocketAddress localAddress,
                            InetSocketAddress peerAddress,
                            int initialDatagramSize,
                            SecureRandom random,
                            PeerConnIdManager peerConnIdManager) {
        this.client = client;
        this.localAddress = Objects.requireNonNull(localAddress, "localAddress");
        this.initialDatagramSize = initialDatagramSize;
        this.random = Objects.requireNonNull(random, "random");
        this.peerConnIdManager = peerConnIdManager;
        PathKey initialKey = PathKey.create(peerAddress);
        current = new Path(initialKey,
                           peerAddress,
                           ++nextGeneration,
                           client,
                           client,
                           initialDatagramSize);
    }

    boolean accepts(SocketAddress source) {
        if (!(source instanceof InetSocketAddress inetSource) || inetSource.isUnresolved()) {
            return false;
        }
        if (closed) {
            return false;
        }
        if (!client) {
            return true;
        }
        return current.key.matches(inetSource);
    }

    ReceiveContext receive(InetSocketAddress source, int datagramBytes) {
        return receive(new ReceiveContext(source, datagramBytes));
    }

    <T extends ReceiveContext> T receive(T context) {
        Objects.requireNonNull(context, "context");
        ReceiveContext receiveContext = context;
        Path active = current;
        boolean activePath = active.key.matches(receiveContext.source);
        PathKey key = activePath ? active.key : PathKey.create(receiveContext.source);
        receiveContext.key = key;
        if (closed) {
            return context;
        }
        if (activePath && (client || active.validated)) {
            receiveContext.credited = true;
            return context;
        }
        lock.lock();
        try {
            Path path = findPath(key);
            if (path != null) {
                path.received = saturatingAdd(path.received, receiveContext.datagramBytes);
                receiveContext.credited = true;
                receiveContext.amplificationBudgetIncreased = receiveContext.datagramBytes > 0 && !path.validated;
            }
        } finally {
            lock.unlock();
        }
        return context;
    }

    boolean knownPath(ReceiveContext context) {
        lock.lock();
        try {
            return findPath(context.key) != null;
        } finally {
            lock.unlock();
        }
    }

    ReceiveResult authenticated(ReceiveContext context,
                                long packetNumber,
                                boolean nonProbing,
                                long validationTimeoutNanos) {
        return authenticated(context, packetNumber, nonProbing, () -> validationTimeoutNanos);
    }

    ReceiveResult authenticated(ReceiveContext context,
                                long packetNumber,
                                boolean nonProbing,
                                LongSupplier validationTimeoutNanos) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(validationTimeoutNanos, "validationTimeoutNanos");
        if (closed) {
            return ReceiveResult.REJECTED;
        }
        Path active = current;
        if (active.key.equals(context.key) && (client || active.validated)) {
            context.authenticatedPath = active;
            if (nonProbing) {
                recordHigherPacketNumber(packetNumber);
            }
            return ReceiveResult.ACCEPTED;
        }
        lock.lock();
        try {
            if (closed) {
                return ReceiveResult.REJECTED;
            }
            Path path = findPath(context.key);
            if (client) {
                if (path == current) {
                    context.authenticatedPath = path;
                }
                return path == current ? ReceiveResult.ACCEPTED : ReceiveResult.REJECTED;
            }
            if (path == null) {
                path = retiredResponsePaths.remove(context.key);
                if (path == null) {
                    path = retiredValidationPaths.remove(context.key);
                } else {
                    retiredValidationPaths.remove(context.key, path);
                }
                if (path == null) {
                    path = new Path(context.key,
                                    context.source,
                                    ++nextGeneration,
                                    false,
                                    false,
                                    initialDatagramSize);
                }
                replaceCandidate(path);
            }
            context.authenticatedPath = path;
            long resolvedValidationTimeoutNanos = Long.MIN_VALUE;
            if (!context.credited) {
                path.received = saturatingAdd(path.received, context.datagramBytes);
                context.credited = true;
                context.amplificationBudgetIncreased = context.datagramBytes > 0 && !path.validated;
            }
            if (path != current && !path.validated && !path.validating) {
                resolvedValidationTimeoutNanos = validationTimeoutNanos.getAsLong();
                queueChallenge(path, resolvedValidationTimeoutNanos, true, true);
            }
            if (!nonProbing || !recordHigherPacketNumber(packetNumber)) {
                return ReceiveResult.ACCEPTED;
            }
            if (path == current) {
                return ReceiveResult.ACCEPTED;
            }
            if (resolvedValidationTimeoutNanos == Long.MIN_VALUE) {
                resolvedValidationTimeoutNanos = validationTimeoutNanos.getAsLong();
            }

            Path oldPath = current;
            Path oldPrevious = previous;
            Path oldPendingPrevious = pendingPrevious;
            if (path.previousValidation) {
                abandonPathValidation(path, false);
                path.previousValidation = false;
                path.previousValidationComplete = false;
            }
            if (candidate != null && candidate != path) {
                retirePath(candidate);
            }
            candidate = null;
            if (path == previous) {
                previous = null;
            }
            if (path == pendingPrevious) {
                pendingPrevious = null;
            }
            if (oldPath.validated) {
                previous = oldPath;
                if (oldPendingPrevious != null
                        && oldPendingPrevious != oldPath
                        && oldPendingPrevious != path) {
                    retirePath(oldPendingPrevious, false);
                }
            } else if (oldPath != path) {
                if (oldPendingPrevious != null
                        && oldPendingPrevious != oldPath
                        && oldPendingPrevious != path) {
                    retirePath(oldPendingPrevious, false);
                }
                pendingPrevious = oldPath;
            }
            if (oldPrevious != null
                    && oldPrevious != previous
                    && oldPrevious != pendingPrevious
                    && oldPrevious != path) {
                retirePath(oldPrevious, false);
            }
            path.generation = ++nextGeneration;
            path.sendReady = false;
            path.retirementPending = false;
            current = path;
            retiredResponsePaths.remove(path.key, path);
            if (!path.validated && !path.validating) {
                queueChallenge(path, resolvedValidationTimeoutNanos, true, true);
            }
            if (oldPath.validated && oldPath != path) {
                if (peerConnIdManager != null && oldPath.binding == null) {
                    oldPath.binding = peerConnIdManager.acquirePathBinding(oldPath.remoteAddress).orElse(null);
                }
                abandonPathValidation(oldPath, false);
                oldPath.previousValidation = true;
                oldPath.previousValidationComplete = false;
                queueChallenge(oldPath, resolvedValidationTimeoutNanos, true, true);
            } else if (oldPath != path && oldPath == pendingPrevious) {
                oldPath.previousValidation = true;
                oldPath.previousValidationComplete = false;
                if (!oldPath.validating) {
                    queueChallenge(oldPath, resolvedValidationTimeoutNanos, true, true);
                }
            }
            recomputeValidationDeadline();
            boolean ipAddressChanged = !oldPath.key.sameIp(path.key);
            return new ReceiveResult(true, true, ipAddressChanged, path.generation);
        } finally {
            lock.unlock();
        }
    }

    void addressValidated(InetSocketAddress address) {
        PathKey key = PathKey.create(address);
        lock.lock();
        try {
            Path path = findPath(key);
            if (path == null) {
                return;
            }
            abandonPathValidation(path, false);
            path.validated = true;
            path.mtuValidated = true;
            path.previousValidation = false;
            path.previousValidationComplete = true;
            promotePendingPrevious(path);
            recomputeValidationDeadline();
        } finally {
            lock.unlock();
        }
    }

    InetSocketAddress peerAddress() {
        return current.remoteAddress;
    }

    InetSocketAddress localAddress() {
        return localAddress;
    }

    long generation() {
        return current.generation;
    }

    void pathChangeCompleted(long generation) {
        lock.lock();
        try {
            Path path = current;
            if (!closed && path.generation == generation) {
                path.sendReady = true;
            }
        } finally {
            lock.unlock();
        }
    }

    int pathMtu() {
        return current.pathMtu;
    }

    Optional<SendPermit> reserve(int requestedBytes) {
        if (requestedBytes <= 0) {
            throw new IllegalArgumentException("Non-positive reservation size: " + requestedBytes);
        }
        if (closed) {
            return Optional.empty();
        }
        Path active = current;
        if (active.sendReady && (client || active.validated)) {
            active.activePermits.incrementAndGet();
            if (!closed && current == active && active.sendReady && !active.retirementPending) {
                return Optional.of(new UnrestrictedSendPermit(this, active, requestedBytes));
            }
            permitCompleted(active);
        }
        lock.lock();
        try {
            Path path = current;
            if (closed || !path.sendReady) {
                return Optional.empty();
            }
            return reserve(path, requestedBytes);
        } finally {
            lock.unlock();
        }
    }

    Optional<SendPermit> reserveValidated(int requestedBytes) {
        if (requestedBytes <= 0) {
            throw new IllegalArgumentException("Non-positive reservation size: " + requestedBytes);
        }
        lock.lock();
        try {
            Path path = current;
            if (closed || !path.sendReady || !path.validated || path.retirementPending) {
                return Optional.empty();
            }
            return reserve(path, requestedBytes);
        } finally {
            lock.unlock();
        }
    }

    Optional<SendPermit> reserve(InetSocketAddress destination, int requestedBytes) {
        if (requestedBytes <= 0) {
            throw new IllegalArgumentException("Non-positive reservation size: " + requestedBytes);
        }
        PathKey key = PathKey.create(destination);
        if (closed) {
            return Optional.empty();
        }
        Path active = current;
        if (active.key.equals(key) && active.sendReady && (client || active.validated)) {
            active.activePermits.incrementAndGet();
            if (!closed && current == active && active.sendReady && !active.retirementPending) {
                return Optional.of(new UnrestrictedSendPermit(this, active, requestedBytes));
            }
            permitCompleted(active);
        }
        lock.lock();
        try {
            if (closed) {
                return Optional.empty();
            }
            Path path = findPath(key);
            if (path == null) {
                return Optional.empty();
            }
            if (path == current && !path.sendReady) {
                return Optional.empty();
            }
            return reserve(path, requestedBytes);
        } finally {
            lock.unlock();
        }
    }

    Optional<SendPermit> reserve(Probe probe, int requestedBytes) {
        Objects.requireNonNull(probe, "probe");
        if (requestedBytes <= 0) {
            throw new IllegalArgumentException("Non-positive reservation size: " + requestedBytes);
        }
        lock.lock();
        try {
            Challenge challenge = probe.challenge ? challenges.get(probe.token) : null;
            if (closed || probe.challenge && (challenge == null || !challenge.path.validating)) {
                return Optional.empty();
            }
            Path path = probe.path;
            if (path == current && !path.sendReady) {
                return Optional.empty();
            }
            return reserve(path, requestedBytes);
        } finally {
            lock.unlock();
        }
    }

    Optional<PeerConnIdManager.PathCidBinding> cidBinding(SendPermit permit) {
        if (peerConnIdManager == null || permit instanceof ClosingSendPermit) {
            return Optional.empty();
        }
        if (permit instanceof UnrestrictedSendPermit unrestrictedPermit) {
            PeerConnIdManager.PathCidBinding capturedBinding = unrestrictedPermit.binding;
            if (capturedBinding != null) {
                return Optional.of(capturedBinding);
            }
            PeerConnIdManager.PathCidBinding binding = unrestrictedPermit.path.binding;
            if (binding != null && peerConnIdManager.acquirePathUse(binding)) {
                unrestrictedPermit.binding = binding;
                return Optional.of(binding);
            }
        }
        lock.lock();
        try {
            Path path;
            PeerConnIdManager.PathCidBinding capturedBinding;
            if (permit instanceof PathSendPermit pathPermit) {
                path = pathPermit.path;
                capturedBinding = pathPermit.binding;
            } else {
                UnrestrictedSendPermit unrestrictedPermit = (UnrestrictedSendPermit) permit;
                path = unrestrictedPermit.path;
                capturedBinding = unrestrictedPermit.binding;
            }
            if (capturedBinding != null) {
                return Optional.of(capturedBinding);
            }
            if (path != current && (current.binding == null || !current.binding.valid())) {
                current.binding = peerConnIdManager.acquirePathBinding(current.remoteAddress).orElse(null);
            }
            if (path.binding == null || !path.binding.valid()) {
                path.binding = peerConnIdManager.acquirePathBinding(path.remoteAddress).orElse(null);
            }
            if (path.binding == null || !peerConnIdManager.acquirePathUse(path.binding)) {
                path.binding = null;
                return Optional.empty();
            }
            if (permit instanceof PathSendPermit pathPermit) {
                pathPermit.binding = path.binding;
            } else {
                ((UnrestrictedSendPermit) permit).binding = path.binding;
            }
            return Optional.ofNullable(path.binding);
        } finally {
            lock.unlock();
        }
    }

    void bindInitialPath() {
        if (peerConnIdManager == null) {
            return;
        }
        lock.lock();
        try {
            if (current.binding == null || !current.binding.valid()) {
                current.binding = peerConnIdManager.acquirePathBinding(current.remoteAddress).orElse(null);
            }
            if (current.binding != null) {
                peerConnIdManager.initialPathUsed(current.binding);
            }
        } finally {
            lock.unlock();
        }
    }

    boolean hasPendingProbe() {
        lock.lock();
        try {
            return !probes.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    Optional<ProbeSend> pollSendableProbe(int requestedBytes) {
        return pollSendableProbe(requestedBytes, false);
    }

    Optional<ProbeSend> pollSendableResponse(int requestedBytes) {
        return pollSendableProbe(requestedBytes, true);
    }

    boolean requeue(Probe probe) {
        lock.lock();
        try {
            Challenge challenge = probe.challenge ? challenges.get(probe.token) : null;
            if (closed || probe.challenge && (challenge == null || !challenge.path.validating)) {
                discardQueuedProbe(probe);
                return false;
            }
            if (probes.size() == MAX_PENDING_PROBES) {
                if (!discardReplaceableQueuedChallenge()) {
                    Probe discarded = null;
                    Iterator<Probe> iterator = probes.descendingIterator();
                    while (iterator.hasNext()) {
                        Probe queued = iterator.next();
                        if (!queued.challenge) {
                            iterator.remove();
                            discarded = queued;
                            break;
                        }
                    }
                    if (discarded == null) {
                        discardQueuedProbe(probe);
                        return false;
                    }
                    discardQueuedProbe(discarded);
                }
            }
            if (probe.challenge) {
                probes.addLast(probe);
            } else {
                queueResponse(probe);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    boolean probeSent(Probe probe, int datagramBytes, long nowNanos) {
        lock.lock();
        try {
            if (!probe.challenge) {
                completeResponse(probe.path);
                return true;
            }
            Challenge challenge = challenges.get(probe.token);
            if (challenge == null || !challenge.path.validating) {
                return false;
            }
            challenge.expanded |= datagramBytes >= MINIMUM_DATAGRAM_SIZE;
            if (!challenge.sent) {
                challenge.sent = true;
                Path path = challenge.path;
                if (path.validationAttempts == 0) {
                    path.validationAbandonNanos = saturatingAdd(nowNanos, path.validationTimeoutNanos);
                }
                path.validationAttempts++;
                long retryDelay = path.validationAttempts == 1
                        ? path.validationRetryNanos
                        : saturatingMultiply(path.validationRetryNanos, 2);
                path.nextValidationNanos = path.validationAttempts < 3
                        ? Math.min(path.validationAbandonNanos,
                                   saturatingAdd(nowNanos, retryDelay))
                        : path.validationAbandonNanos;
                recomputeValidationDeadline();
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    void pathChallenge(ReceiveContext context, ByteBuffer data) {
        long token = token(data);
        lock.lock();
        try {
            Path path = context.authenticatedPath;
            if (path == null) {
                path = findPath(context.key);
            }
            if (closed || path == null) {
                return;
            }
            int pendingResponses = 0;
            for (Probe probe : probes) {
                if (!probe.challenge) {
                    pendingResponses++;
                }
            }
            if (pendingResponses == MAX_PENDING_RESPONSES) {
                Probe discarded = null;
                Iterator<Probe> iterator = probes.iterator();
                while (iterator.hasNext()) {
                    Probe queued = iterator.next();
                    if (!queued.challenge) {
                        iterator.remove();
                        discarded = queued;
                        break;
                    }
                }
                discardQueuedProbe(discarded);
            } else if (probes.size() == MAX_PENDING_PROBES) {
                if (!discardReplaceableQueuedChallenge()) {
                    Probe discarded = null;
                    Iterator<Probe> iterator = probes.iterator();
                    while (iterator.hasNext()) {
                        Probe queued = iterator.next();
                        if (!queued.challenge) {
                            iterator.remove();
                            discarded = queued;
                            break;
                        }
                    }
                    if (discarded == null) {
                        return;
                    }
                    discardQueuedProbe(discarded);
                }
            }
            Probe response = new Probe(path,
                                       PathResponseFrame.create(data.asReadOnlyBuffer()),
                                       true,
                                       token,
                                       false,
                                       path == current);
            path.pendingResponses++;
            queueResponse(response);
            if (findPath(context.key) != path) {
                path.retirementPending = true;
                retainResponsePath(path);
            }
        } finally {
            lock.unlock();
        }
    }

    boolean pathResponse(ByteBuffer data, long nowNanos, long validationTimeoutNanos) {
        long token = token(data);
        lock.lock();
        try {
            Challenge challenge = challenges.remove(token);
            if (challenge == null) {
                return retiredChallenges.containsKey(token);
            }
            Path path = findPath(challenge.path.key);
            if (path == null) {
                path = challenge.path;
                retiredValidationPaths.remove(path.key, path);
                replaceCandidate(path);
            }
            retireChallenge(token);
            boolean previousValidation = path.previousValidation;
            abandonPathValidation(path, false);
            path.validated = true;
            if (previousValidation) {
                path.previousValidation = false;
                path.previousValidationComplete = true;
                path.mtuValidated |= challenge.expanded;
                if (!challenge.expanded) {
                    queueChallenge(path, validationTimeoutNanos, true, true);
                }
            } else if (challenge.expanded) {
                path.mtuValidated = true;
            } else {
                queueChallenge(path, validationTimeoutNanos, true, true);
            }
            if (path == current && path.mtuValidated && candidate != null) {
                Path retired = candidate;
                candidate = null;
                retirePath(retired, false);
            } else if (path == candidate && current.mtuValidated) {
                candidate = null;
                retirePath(path, false);
            }
            promotePendingPrevious(path);
            recomputeValidationDeadline();
            return true;
        } finally {
            lock.unlock();
        }
    }

    OptionalLong nextValidationDeadlineNanos() {
        long deadline = validationDeadlineNanos;
        return deadline == Long.MAX_VALUE ? OptionalLong.empty() : OptionalLong.of(deadline);
    }

    OptionalLong pollRetiredPathGeneration() {
        Long generation = retiredPathGenerations.poll();
        return generation == null ? OptionalLong.empty() : OptionalLong.of(generation);
    }

    TimeoutResult validationTimedOut(long nowNanos) {
        lock.lock();
        try {
            if (closed || validationDeadlineNanos == Long.MAX_VALUE || nowNanos < validationDeadlineNanos) {
                return TimeoutResult.NONE;
            }
            Path validatingPath = null;
            for (Path path : new Path[] {current, candidate, previous, pendingPrevious}) {
                if (path != null
                        && path.validating
                        && path.nextValidationNanos <= nowNanos
                        && (validatingPath == null
                                || path.nextValidationNanos < validatingPath.nextValidationNanos)) {
                    validatingPath = path;
                }
            }
            for (Path path : retiredValidationPaths.values()) {
                if (path.validating
                        && path.nextValidationNanos <= nowNanos
                        && (validatingPath == null
                                || path.nextValidationNanos < validatingPath.nextValidationNanos)) {
                    validatingPath = path;
                }
            }
            if (validatingPath == null) {
                recomputeValidationDeadline();
                return TimeoutResult.NONE;
            }
            if (nowNanos < validatingPath.validationAbandonNanos) {
                validatingPath.nextValidationNanos = validatingPath.validationAbandonNanos;
                queueChallenge(validatingPath, 0, true, false);
                recomputeValidationDeadline();
                return TimeoutResult.NONE;
            }
            Path failed = validatingPath;
            boolean previousValidation = failed.previousValidation;
            if (retiredValidationPaths.remove(failed.key, failed)) {
                abandonPathValidation(failed, false);
                maybeRetirePath(failed);
                recomputeValidationDeadline();
                return TimeoutResult.NONE;
            }
            retirePath(failed, false);
            if (failed == candidate) {
                candidate = null;
                recomputeValidationDeadline();
                return TimeoutResult.NONE;
            }
            if (failed == previous) {
                previous = null;
                failed.validated = false;
                failed.previousValidation = false;
                recomputeValidationDeadline();
                return TimeoutResult.NONE;
            }
            if (failed == pendingPrevious) {
                pendingPrevious = null;
                failed.validated = false;
                failed.previousValidation = false;
                recomputeValidationDeadline();
                return TimeoutResult.NONE;
            }
            if (failed != current || failed.mtuValidated && !previousValidation) {
                recomputeValidationDeadline();
                return TimeoutResult.NONE;
            }
            if (previous == null || !previous.validated) {
                recomputeValidationDeadline();
                return TimeoutResult.FAILED;
            }
            Path fallback = previous;
            previous = null;
            if (pendingPrevious != null) {
                Path retired = pendingPrevious;
                pendingPrevious = null;
                retirePath(retired, false);
            }
            fallback.generation = ++nextGeneration;
            fallback.sendReady = false;
            fallback.retirementPending = false;
            current = fallback;
            recomputeValidationDeadline();
            return new TimeoutResult(true, !failed.key.sameIp(fallback.key), fallback.generation, false);
        } finally {
            lock.unlock();
        }
    }

    ClosingPath closingPath() {
        lock.lock();
        try {
            Path path = current;
            return new ClosingPath(path.remoteAddress,
                                   path.generation,
                                   client || path.validated,
                                   path.received,
                                   path.sent,
                                   path.reserved);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            closed = true;
            probes.clear();
            challenges.clear();
            retiredChallenges.clear();
            retiredResponsePaths.clear();
            retiredValidationPaths.clear();
            candidate = null;
            previous = null;
            pendingPrevious = null;
            validationDeadlineNanos = Long.MAX_VALUE;
        } finally {
            lock.unlock();
        }
    }

    private static long token(ByteBuffer data) {
        ByteBuffer copy = data.asReadOnlyBuffer();
        if (copy.remaining() != Long.BYTES) {
            throw new IllegalArgumentException("Path validation data must contain exactly 8 bytes");
        }
        return copy.getLong();
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long saturatingMultiply(long value, int multiplier) {
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    private Optional<ProbeSend> pollSendableProbe(int requestedBytes, boolean responseOnly) {
        if (requestedBytes <= 0) {
            throw new IllegalArgumentException("Non-positive reservation size: " + requestedBytes);
        }
        lock.lock();
        try {
            Iterator<Probe> iterator = probes.iterator();
            while (iterator.hasNext()) {
                Probe probe = iterator.next();
                if (responseOnly && probe.challenge) {
                    continue;
                }
                Challenge challenge = probe.challenge ? challenges.get(probe.token) : null;
                if (probe.challenge && (challenge == null || !challenge.path.validating)) {
                    iterator.remove();
                    discardQueuedProbe(probe);
                    continue;
                }
                if (probe.path == current && !probe.path.sendReady) {
                    continue;
                }
                Optional<SendPermit> reservation = reserve(probe.path, requestedBytes);
                if (reservation.isEmpty()) {
                    continue;
                }
                SendPermit permit = reservation.orElseThrow();
                Optional<PeerConnIdManager.PathCidBinding> binding = cidBinding(permit);
                if (binding.isEmpty()) {
                    permit.release();
                    continue;
                }
                iterator.remove();
                return Optional.of(new ProbeSend(probe, permit, binding.orElseThrow()));
            }
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    private Path findPath(PathKey key) {
        Path path = current;
        if (path.key.equals(key)) {
            return path;
        }
        path = previous;
        if (path != null && path.key.equals(key)) {
            return path;
        }
        path = pendingPrevious;
        if (path != null && path.key.equals(key)) {
            return path;
        }
        path = candidate;
        return path != null && path.key.equals(key) ? path : null;
    }

    private void promotePendingPrevious(Path path) {
        if (path != pendingPrevious
                || !path.validated
                || !path.mtuValidated
                || !path.previousValidationComplete) {
            return;
        }
        pendingPrevious = null;
        Path displaced = previous;
        previous = path;
        if (displaced != null && displaced != current && displaced != path) {
            retirePath(displaced, false);
        }
    }

    private void replaceCandidate(Path replacement) {
        if (candidate != null && candidate != replacement) {
            retirePath(candidate);
        }
        candidate = replacement;
        replacement.retirementPending = false;
        retiredResponsePaths.remove(replacement.key, replacement);
        retiredValidationPaths.remove(replacement.key, replacement);
    }

    private void retirePath(Path path) {
        retirePath(path, true);
    }

    private void retirePath(Path path, boolean preserveSentChallenges) {
        if (!path.retirementPending) {
            retiredPathGenerations.add(path.generation);
        }
        path.retirementPending = true;
        abandonPathValidation(path, preserveSentChallenges);
        retainResponsePath(path);
        maybeRetirePath(path);
    }

    private void abandonPathValidation(Path path, boolean preserveSentChallenges) {
        path.validating = false;
        path.nextValidationNanos = Long.MAX_VALUE;
        boolean challengeRetained = false;
        Iterator<Map.Entry<Long, Challenge>> iterator = challenges.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Challenge> entry = iterator.next();
            Challenge challenge = entry.getValue();
            if (challenge.path.key.equals(path.key)) {
                if (preserveSentChallenges && challenge.sent) {
                    challengeRetained = true;
                    continue;
                }
                retireChallenge(entry.getKey());
                iterator.remove();
            }
        }
        probes.removeIf(probe -> probe.challenge && probe.path.key.equals(path.key));
        retiredValidationPaths.remove(path.key, path);
        if (challengeRetained) {
            path.validating = true;
            path.nextValidationNanos = path.validationAbandonNanos;
            retiredValidationPaths.put(path.key, path);
            if (retiredValidationPaths.size() > MAX_RETIRED_VALIDATION_PATHS) {
                Iterator<Path> paths = retiredValidationPaths.values().iterator();
                Path evicted = paths.next();
                paths.remove();
                abandonPathValidation(evicted, false);
                maybeRetirePath(evicted);
            }
        }
        recomputeValidationDeadline();
    }

    private void discardQueuedProbe(Probe probe) {
        if (probe == null) {
            return;
        }
        if (probe.challenge) {
            discardChallenge(probe.token, false);
        } else {
            completeResponse(probe.path);
        }
    }

    private void discardChallenge(long token, boolean removeQueuedProbe) {
        Challenge removed = challenges.remove(token);
        if (removed == null) {
            return;
        }
        retireChallenge(token);
        if (removeQueuedProbe) {
            probes.removeIf(probe -> probe.challenge && probe.token == token);
        }
        boolean challengeRemains = challenges.values().stream()
                .anyMatch(challenge -> challenge.path == removed.path);
        if (!challengeRemains) {
            removed.path.validating = false;
            removed.path.nextValidationNanos = Long.MAX_VALUE;
            retiredValidationPaths.remove(removed.path.key, removed.path);
            recomputeValidationDeadline();
        }
        maybeRetirePath(removed.path);
    }

    private void queueResponse(Probe response) {
        Deque<Probe> reordered = new ArrayDeque<>(probes.size() + 1);
        boolean responseAdded = false;
        for (Probe queued : probes) {
            if (!responseAdded && queued.challenge) {
                reordered.addLast(response);
                responseAdded = true;
            }
            reordered.addLast(queued);
        }
        if (!responseAdded) {
            reordered.addLast(response);
        }
        probes.clear();
        probes.addAll(reordered);
    }

    private boolean discardReplaceableQueuedChallenge() {
        Iterator<Probe> iterator = probes.descendingIterator();
        while (iterator.hasNext()) {
            Probe probe = iterator.next();
            if (!probe.challenge) {
                continue;
            }
            Challenge challenge = challenges.get(probe.token);
            if (challenge == null || !mustPreserveChallenge(challenge)) {
                iterator.remove();
                discardQueuedProbe(probe);
                return true;
            }
        }
        return false;
    }

    private boolean mustPreserveChallenge(Challenge challenge) {
        Path path = challenge.path;
        if (path != current && path != pendingPrevious && path != previous) {
            return false;
        }
        int pathChallenges = 0;
        for (Challenge existing : challenges.values()) {
            if (existing.path == path && ++pathChallenges > 1) {
                return false;
            }
        }
        return true;
    }

    private void completeResponse(Path path) {
        if (path.pendingResponses > 0) {
            path.pendingResponses--;
        }
        if (path.pendingResponses == 0) {
            retiredResponsePaths.remove(path.key, path);
            maybeRetirePath(path);
        }
    }

    private void retainResponsePath(Path path) {
        if (path.pendingResponses == 0) {
            return;
        }
        retiredResponsePaths.put(path.key, path);
        if (retiredResponsePaths.size() > MAX_RETIRED_RESPONSE_PATHS) {
            Iterator<PathKey> iterator = retiredResponsePaths.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private void permitCompleted(Path path) {
        int remaining = path.activePermits.decrementAndGet();
        if (remaining == 0 && path.retirementPending) {
            lock.lock();
            try {
                maybeRetirePath(path);
            } finally {
                lock.unlock();
            }
        }
    }

    private void releaseCidUse(PeerConnIdManager.PathCidBinding binding) {
        if (peerConnIdManager != null && binding != null) {
            peerConnIdManager.releasePathUse(binding);
        }
    }

    private void commitCidUse(PeerConnIdManager.PathCidBinding binding) {
        if (peerConnIdManager != null && binding != null) {
            peerConnIdManager.commitPathUse(binding);
        }
    }

    private void maybeRetirePath(Path path) {
        if (!path.retirementPending || path.activePermits.get() != 0 || path.pendingResponses != 0) {
            return;
        }
        for (Challenge challenge : challenges.values()) {
            if (challenge.path == path) {
                return;
            }
        }
        path.retirementPending = false;
        retiredPathGenerations.add(path.generation);
        if (peerConnIdManager != null && path.binding != null) {
            peerConnIdManager.retirePathBinding(path.binding);
            path.binding = null;
        }
    }

    private void retireChallenge(long token) {
        retiredChallenges.put(token, Boolean.TRUE);
        if (retiredChallenges.size() > MAX_RETIRED_CHALLENGES) {
            Iterator<Long> iterator = retiredChallenges.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private boolean recordHigherPacketNumber(long packetNumber) {
        long witness;
        do {
            witness = highestPathSelectionPacket.get();
            if (packetNumber <= witness) {
                return false;
            }
        } while (!highestPathSelectionPacket.compareAndSet(witness, packetNumber));
        return true;
    }

    private void queueChallenge(Path path,
                                long validationTimeoutNanos,
                                boolean padToMinimum,
                                boolean restartTimer) {
        if (challenges.size() == MAX_CHALLENGES) {
            Iterator<Map.Entry<Long, Challenge>> iterator = challenges.entrySet().iterator();
            Long discardedToken = null;
            while (iterator.hasNext()) {
                Map.Entry<Long, Challenge> entry = iterator.next();
                if (!mustPreserveChallenge(entry.getValue())) {
                    discardedToken = entry.getKey();
                    break;
                }
            }
            if (discardedToken == null) {
                return;
            }
            discardChallenge(discardedToken, true);
        }
        long token;
        do {
            token = random.nextLong();
        } while (challenges.containsKey(token));
        Challenge challenge = new Challenge(path);
        challenges.put(token, challenge);
        if (restartTimer) {
            path.validationTimeoutNanos = validationTimeoutNanos;
            path.validationRetryNanos = Math.max(1, validationTimeoutNanos / 3);
            path.validationAttempts = 0;
            path.validationAbandonNanos = Long.MAX_VALUE;
            path.nextValidationNanos = Long.MAX_VALUE;
        }
        path.validating = true;
        if (probes.size() == MAX_PENDING_PROBES) {
            if (!discardReplaceableQueuedChallenge()) {
                discardChallenge(token, false);
                return;
            }
        }
        ByteBuffer challengeData = ByteBuffer.allocate(Long.BYTES).putLong(token).flip();
        probes.addLast(new Probe(path,
                                 PathChallengeFrame.create(challengeData),
                                 padToMinimum,
                                 token,
                                 true,
                                 false));
    }

    private void recomputeValidationDeadline() {
        long deadline = Long.MAX_VALUE;
        for (Path path : new Path[] {current, candidate, previous, pendingPrevious}) {
            if (path != null && path.validating) {
                deadline = Math.min(deadline, path.nextValidationNanos);
            }
        }
        for (Path path : retiredValidationPaths.values()) {
            if (path.validating) {
                deadline = Math.min(deadline, path.nextValidationNanos);
            }
        }
        validationDeadlineNanos = deadline;
    }

    private Optional<SendPermit> reserve(Path path, int requestedBytes) {
        int reservedBytes = requestedBytes;
        if (!client && !path.validated) {
            long limit = saturatingMultiply(path.received, 3);
            long available = Math.max(0, limit - saturatingAdd(path.sent, path.reserved));
            reservedBytes = (int) Math.min(requestedBytes, available);
            if (reservedBytes == 0) {
                return Optional.empty();
            }
        }
        path.reserved = saturatingAdd(path.reserved, reservedBytes);
        path.activePermits.incrementAndGet();
        return Optional.of(new PathSendPermit(this, path, reservedBytes));
    }

    interface SendPermit {
        InetSocketAddress destination();

        long generation();

        int size();

        void resize(int size);

        void commit();

        void release();

        PeerConnIdManager.PathCidBinding binding();
    }

    static class ReceiveContext {
        private final InetSocketAddress source;
        private final int datagramBytes;
        private PathKey key;
        private boolean credited;
        private boolean amplificationBudgetIncreased;
        private Path authenticatedPath;

        ReceiveContext(InetSocketAddress source, int datagramBytes) {
            if (datagramBytes < 0) {
                throw new IllegalArgumentException("Negative datagram size: " + datagramBytes);
            }
            this.source = Objects.requireNonNull(source, "source");
            this.datagramBytes = datagramBytes;
        }

        InetSocketAddress source() {
            return source;
        }

        boolean credited() {
            return credited;
        }

        boolean amplificationBudgetIncreased() {
            return amplificationBudgetIncreased;
        }
    }

    record ReceiveResult(boolean accepted, boolean pathChanged, boolean ipAddressChanged, long generation) {
        private static final ReceiveResult REJECTED = new ReceiveResult(false, false, false, -1);
        private static final ReceiveResult ACCEPTED = new ReceiveResult(true, false, false, -1);
    }

    record TimeoutResult(boolean pathChanged, boolean ipAddressChanged, long generation, boolean pathFailed) {
        private static final TimeoutResult NONE = new TimeoutResult(false, false, -1, false);
        private static final TimeoutResult FAILED = new TimeoutResult(false, false, -1, true);
    }

    record ProbeSend(Probe probe,
                     SendPermit permit,
                     PeerConnIdManager.PathCidBinding binding) {
    }

    static final class Probe {
        private final Path path;
        private final QuicFrame frame;
        private final boolean padToMinimum;
        private final long token;
        private final boolean challenge;
        private final boolean ping;

        private Probe(Path path,
                      QuicFrame frame,
                      boolean padToMinimum,
                      long token,
                      boolean challenge,
                      boolean ping) {
            this.path = path;
            this.frame = frame;
            this.padToMinimum = padToMinimum;
            this.token = token;
            this.challenge = challenge;
            this.ping = ping;
        }

        InetSocketAddress destination() {
            return path.remoteAddress;
        }

        QuicFrame frame() {
            return frame;
        }

        boolean padToMinimum() {
            return padToMinimum;
        }

        long token() {
            return token;
        }

        boolean challenge() {
            return challenge;
        }

        boolean ping() {
            return ping;
        }
    }

    // Closing replay uses pre-encoded packets whose destination CID is already fixed. Keep this snapshot detached from the
    // live path and connection-ID managers so protected close cannot retain the terminated connection graph.
    static final class ClosingPath {
        private final InetSocketAddress destination;
        private final long generation;
        private final boolean unrestricted;
        private final ReentrantLock lock = new ReentrantLock();
        private long received;
        private long sent;
        private long reserved;

        private ClosingPath(InetSocketAddress destination,
                            long generation,
                            boolean unrestricted,
                            long received,
                            long sent,
                            long reserved) {
            this.destination = destination;
            this.generation = generation;
            this.unrestricted = unrestricted;
            this.received = received;
            this.sent = sent;
            this.reserved = reserved;
        }

        boolean accepts(SocketAddress source) {
            return destination.equals(source);
        }

        void received(int bytes) {
            lock.lock();
            try {
                received = saturatingAdd(received, bytes);
            } finally {
                lock.unlock();
            }
        }

        Optional<SendPermit> reserve(int requestedBytes) {
            lock.lock();
            try {
                int reservation = requestedBytes;
                if (!unrestricted) {
                    long limit = saturatingMultiply(received, 3);
                    long available = Math.max(0, limit - saturatingAdd(sent, reserved));
                    reservation = (int) Math.min(requestedBytes, available);
                    if (reservation < requestedBytes) {
                        return Optional.empty();
                    }
                }
                reserved = saturatingAdd(reserved, reservation);
                return Optional.of(new ClosingSendPermit(this, reservation));
            } finally {
                lock.unlock();
            }
        }
    }

    private static final class PathSendPermit implements SendPermit {
        private final QuicPathManager manager;
        private final Path path;
        private final InetSocketAddress destination;
        private final long generation;
        private PeerConnIdManager.PathCidBinding binding;
        private volatile int size;
        private boolean complete;

        private PathSendPermit(QuicPathManager manager, Path path, int size) {
            this.manager = manager;
            this.path = path;
            this.destination = path.remoteAddress;
            this.generation = path.generation;
            this.size = size;
        }

        @Override
        public InetSocketAddress destination() {
            return destination;
        }

        @Override
        public long generation() {
            return generation;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void resize(int newSize) {
            manager.lock.lock();
            try {
                if (complete) {
                    return;
                }
                if (newSize <= 0 || newSize > size) {
                    throw new IllegalArgumentException("Invalid reservation resize from " + size + " to " + newSize);
                }
                path.reserved -= size - newSize;
                size = newSize;
            } finally {
                manager.lock.unlock();
            }
        }

        @Override
        public void commit() {
            boolean completed = false;
            manager.lock.lock();
            try {
                if (complete) {
                    return;
                }
                complete = true;
                path.reserved -= size;
                path.sent = saturatingAdd(path.sent, size);
                manager.permitCompleted(path);
                completed = true;
            } finally {
                manager.lock.unlock();
            }
            if (completed) {
                manager.commitCidUse(binding);
            }
        }

        @Override
        public void release() {
            boolean completed = false;
            manager.lock.lock();
            try {
                if (complete) {
                    return;
                }
                complete = true;
                path.reserved -= size;
                manager.permitCompleted(path);
                completed = true;
            } finally {
                manager.lock.unlock();
            }
            if (completed) {
                manager.releaseCidUse(binding);
            }
        }

        @Override
        public PeerConnIdManager.PathCidBinding binding() {
            return binding;
        }
    }

    private static final class UnrestrictedSendPermit implements SendPermit {
        private static final AtomicIntegerFieldUpdater<UnrestrictedSendPermit> COMPLETE =
                AtomicIntegerFieldUpdater.newUpdater(UnrestrictedSendPermit.class, "complete");

        private final QuicPathManager manager;
        private final Path path;
        private final long generation;
        private volatile PeerConnIdManager.PathCidBinding binding;
        private volatile int size;
        private volatile int complete;

        private UnrestrictedSendPermit(QuicPathManager manager, Path path, int size) {
            this.manager = manager;
            this.path = path;
            this.generation = path.generation;
            this.size = size;
        }

        @Override
        public InetSocketAddress destination() {
            return path.remoteAddress;
        }

        @Override
        public long generation() {
            return generation;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void resize(int newSize) {
            if (newSize <= 0 || newSize > size) {
                throw new IllegalArgumentException("Invalid reservation resize from " + size + " to " + newSize);
            }
            size = newSize;
        }

        @Override
        public void commit() {
            if (COMPLETE.compareAndSet(this, 0, 1)) {
                manager.permitCompleted(path);
                manager.commitCidUse(binding);
            }
        }

        @Override
        public void release() {
            if (COMPLETE.compareAndSet(this, 0, 1)) {
                manager.permitCompleted(path);
                manager.releaseCidUse(binding);
            }
        }

        @Override
        public PeerConnIdManager.PathCidBinding binding() {
            return binding;
        }
    }

    private static final class ClosingSendPermit implements SendPermit {
        private final ClosingPath path;
        private volatile int size;
        private boolean complete;

        private ClosingSendPermit(ClosingPath path, int size) {
            this.path = path;
            this.size = size;
        }

        @Override
        public InetSocketAddress destination() {
            return path.destination;
        }

        @Override
        public long generation() {
            return path.generation;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void resize(int newSize) {
            path.lock.lock();
            try {
                if (complete) {
                    return;
                }
                if (newSize <= 0 || newSize > size) {
                    throw new IllegalArgumentException("Invalid reservation resize from " + size + " to " + newSize);
                }
                path.reserved -= size - newSize;
                size = newSize;
            } finally {
                path.lock.unlock();
            }
        }

        @Override
        public void commit() {
            path.lock.lock();
            try {
                if (complete) {
                    return;
                }
                complete = true;
                path.reserved -= size;
                path.sent = saturatingAdd(path.sent, size);
            } finally {
                path.lock.unlock();
            }
        }

        @Override
        public void release() {
            path.lock.lock();
            try {
                if (complete) {
                    return;
                }
                complete = true;
                path.reserved -= size;
            } finally {
                path.lock.unlock();
            }
        }

        @Override
        public PeerConnIdManager.PathCidBinding binding() {
            return null;
        }
    }

    private static final class Path {
        private final PathKey key;
        private final InetSocketAddress remoteAddress;
        private final int pathMtu;
        private final AtomicInteger activePermits = new AtomicInteger();
        private volatile PeerConnIdManager.PathCidBinding binding;
        private volatile long generation;
        private volatile boolean validated;
        private volatile boolean sendReady = true;
        private boolean mtuValidated;
        private boolean validating;
        private long received;
        private long reserved;
        private long sent;
        private long validationAbandonNanos = Long.MAX_VALUE;
        private long validationTimeoutNanos;
        private long validationRetryNanos;
        private long nextValidationNanos = Long.MAX_VALUE;
        private int validationAttempts;
        private int pendingResponses;
        private volatile boolean retirementPending;
        private boolean previousValidation;
        private boolean previousValidationComplete;

        private Path(PathKey key,
                     InetSocketAddress remoteAddress,
                     long generation,
                     boolean validated,
                     boolean mtuValidated,
                     int initialDatagramSize) {
            this.key = key;
            this.remoteAddress = remoteAddress;
            this.generation = generation;
            this.validated = validated;
            this.mtuValidated = mtuValidated;
            int maximum = remoteAddress.getAddress() instanceof Inet6Address
                    ? MAX_IPV6_DATAGRAM_SIZE
                    : MAX_IPV4_DATAGRAM_SIZE;
            this.pathMtu = Math.clamp(initialDatagramSize, MINIMUM_DATAGRAM_SIZE, maximum);
        }
    }

    private static final class PathKey {
        private final InetAddress address;
        private final int port;
        private final int scope;
        private final int hashCode;

        private PathKey(InetAddress address, int port, int scope) {
            this.address = address;
            this.port = port;
            this.scope = scope;
            this.hashCode = 31 * (31 * address.hashCode() + port) + scope;
        }

        static PathKey create(InetSocketAddress address) {
            Objects.requireNonNull(address, "address");
            if (address.isUnresolved()) {
                throw new IllegalArgumentException("Unresolved path address: " + address);
            }
            InetAddress inetAddress = address.getAddress();
            int scope = inetAddress instanceof Inet6Address inet6 ? inet6.getScopeId() : 0;
            return new PathKey(inetAddress, address.getPort(), scope);
        }

        boolean matches(InetSocketAddress other) {
            if (other.isUnresolved() || port != other.getPort()) {
                return false;
            }
            InetAddress otherAddress = other.getAddress();
            int otherScope = otherAddress instanceof Inet6Address inet6 ? inet6.getScopeId() : 0;
            return scope == otherScope && address.equals(otherAddress);
        }

        boolean sameIp(PathKey other) {
            return scope == other.scope && address.equals(other.address);
        }

        @Override
        public boolean equals(Object object) {
            return this == object
                    || object instanceof PathKey other
                    && port == other.port
                    && sameIp(other);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class Challenge {
        private final Path path;
        private boolean sent;
        private boolean expanded;

        private Challenge(Path path) {
            this.path = path;
        }
    }
}
