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

package io.helidon.messaging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One validated messaging topology and lifecycle.
 */
final class DefaultMessagingGraph implements MessagingGraph {
    private static final System.Logger LOGGER = System.getLogger(DefaultMessagingGraph.class.getName());
    private static final AtomicLong LIFECYCLE_SEQUENCE = new AtomicLong();

    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final ThreadLocal<Boolean> lifecycleCallback = new ThreadLocal<>();
    private final DeliveryEngine deliveryEngine;
    private final Map<String, MessagingChannel<?>> channels = new LinkedHashMap<>();
    private final Map<MessagingChannel<?>, Emitter<?>> emitters = new IdentityHashMap<>();
    private final Map<String, Set<String>> routes = new LinkedHashMap<>();
    private final Map<String, SourceBinding> sources = new LinkedHashMap<>();
    private final Set<Runnable> sourceIdentities =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Connector> connectorBindings = new ArrayList<>();
    private final List<OutgoingConnector> outgoingConnectors = new ArrayList<>();
    private final Set<Connector> connectorIdentities =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final AtomicBoolean shutdownOwner = new AtomicBoolean();
    private final CompletableFuture<Void> preparationCompletion = new CompletableFuture<>();
    private final CompletableFuture<Void> startupCompletion = new CompletableFuture<>();
    private final CompletableFuture<Void> shutdownCompletion = new CompletableFuture<>();
    private volatile Map<String, Integer> effectiveDeliveryLimits = Map.of();
    private boolean preparationInProgress;
    private boolean sealed;
    private volatile State state = State.NEW;
    private volatile Throwable failure;

    DefaultMessagingGraph(DeliveryEngine deliveryEngine) {
        this.deliveryEngine = Objects.requireNonNull(deliveryEngine);
    }

    DeliveryEngine deliveryEngine() {
        return deliveryEngine;
    }

    int maxDeliveryMessages(String channel) {
        Integer limit = effectiveDeliveryLimits.get(channel);
        if (limit != null) {
            return limit;
        }
        if (!channels.containsKey(channel)) {
            throw new MessagingException("Unknown messaging channel " + channel);
        }
        throw new IllegalStateException("Messaging graph topology is not finalized");
    }

    State state() {
        return state;
    }

    Optional<Throwable> failure() {
        return Optional.ofNullable(failure);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Emitter<T> emitter(MessagingChannel<T> channel) {
        Objects.requireNonNull(channel);
        lifecycleLock.lock();
        try {
            Emitter<?> emitter = emitters.get(channel);
            if (emitter == null) {
                throw new IllegalArgumentException("Messaging channel " + channel.name()
                                                           + " is not owned by this messaging graph");
            }
            return (Emitter<T>) emitter;
        } finally {
            lifecycleLock.unlock();
        }
    }

    <T> void addEmitter(MessagingChannel<T> channel, Emitter<T> emitter) {
        Objects.requireNonNull(channel);
        Objects.requireNonNull(emitter);
        lifecycleLock.lock();
        try {
            requireMutable();
            if (emitters.containsKey(channel)) {
                throw new IllegalArgumentException("Messaging channel handle is already registered");
            }
            emitters.put(channel, new GraphEmitter<>(this, emitter));
        } finally {
            lifecycleLock.unlock();
        }
    }

    void addChannel(String name,
                    MessagingChannel<?> channel,
                    MessagingExecutionConfig executionConfig) {
        addChannelContribution(name, channel, executionConfig, Map.of(), List.of(), List.of());
    }

    void addChannelContribution(String name,
                                MessagingChannel<?> channel,
                                MessagingExecutionConfig executionConfig,
                                Map<String, Runnable> channelSources,
                                List<?> bindings,
                                List<String> inputChannels) {
        addChannelContribution(name,
                               channel,
                               executionConfig,
                               channelSources,
                               bindings,
                               inputChannels,
                               () -> { });
    }

    void addChannelContribution(String name,
                                MessagingChannel<?> channel,
                                MessagingExecutionConfig executionConfig,
                                Map<String, Runnable> channelSources,
                                List<?> bindings,
                                List<String> inputChannels,
                                Runnable connectInputs) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(channel);
        Objects.requireNonNull(executionConfig);
        Objects.requireNonNull(channelSources);
        Objects.requireNonNull(bindings);
        Objects.requireNonNull(inputChannels);
        Objects.requireNonNull(connectInputs);
        lifecycleLock.lock();
        try {
            requireMutable();
            if (channels.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate messaging channel " + name);
            }
            validateContributionSources(channelSources);
            validateContributionBindings(bindings, channelSources.values());
            Set<String> uniqueInputs = new LinkedHashSet<>(inputChannels);
            if (uniqueInputs.size() != inputChannels.size()) {
                throw new IllegalArgumentException("Duplicate imperative input channel for " + name);
            }
            for (String inputChannel : uniqueInputs) {
                if (!channels.containsKey(inputChannel)) {
                    throw new IllegalArgumentException("Unknown imperative input channel " + inputChannel);
                }
            }

            deliveryEngine.registerChannel(name, executionConfig);
            channels.put(name, channel);
            channelSources.forEach(this::addSourceLocked);
            bindings.forEach(this::addBindingLocked);
            for (String inputChannel : uniqueInputs) {
                routes.computeIfAbsent(inputChannel, ignored -> new LinkedHashSet<>()).add(name);
            }
            connectInputs.run();
        } finally {
            lifecycleLock.unlock();
        }
    }

    Optional<MessagingChannel<?>> channel(String name) {
        lifecycleLock.lock();
        try {
            return Optional.ofNullable(channels.get(name));
        } finally {
            lifecycleLock.unlock();
        }
    }

    void addRoute(String source, String target) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(target);
        lifecycleLock.lock();
        try {
            requireMutable();
            routes.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(target);
        } finally {
            lifecycleLock.unlock();
        }
    }

    void addBinding(Object binding) {
        lifecycleLock.lock();
        try {
            requireMutable();
            validateContributionBindings(List.of(binding), List.of());
            addBindingLocked(binding);
        } finally {
            lifecycleLock.unlock();
        }
    }

    void addSource(String name, Runnable source) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(source);
        lifecycleLock.lock();
        try {
            requireMutable();
            validateContributionSources(Map.of(name, source));
            validateContributionBindings(List.of(), List.of(source));
            addSourceLocked(name, source);
        } finally {
            lifecycleLock.unlock();
        }
    }

    void addIncomingConnector(String name,
                              IncomingConnector connector,
                              IncomingConnectorContext context) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(connector);
        Objects.requireNonNull(context);
        lifecycleLock.lock();
        try {
            requireMutable();
            if (sources.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate messaging source " + name);
            }
            if (connectorIdentities.contains(connector)) {
                throw new IllegalArgumentException("Connector is already owned by this messaging graph");
            }
            sources.put(name, new SourceBinding(name, connector, context));
            connectorIdentities.add(connector);
        } finally {
            lifecycleLock.unlock();
        }
    }

    void addIncomingConnector(String name, IncomingConnector connector) {
        addIncomingConnector(name, connector, new IncomingConnectorContext() {
            @Override
            public String channel() {
                return name;
            }

            @Override
            public ConnectorDeliveryReservation reserveDelivery() {
                throw new UnsupportedOperationException("Test incoming context does not deliver messages");
            }

            @Override
            public Optional<ConnectorDeliveryReservation> tryReserveDelivery() {
                throw new UnsupportedOperationException("Test incoming context does not deliver messages");
            }
        });
    }

    void seal() {
        lifecycleLock.lock();
        try {
            requireMutable();
            finalizeTopology();
            sealed = true;
        } finally {
            lifecycleLock.unlock();
        }
    }

    void prepare() {
        prepareIfNeeded(true);
    }

    private void prepareIfNeeded(boolean rejectIfNotNeeded) {
        boolean preparationOwner = false;
        boolean preparationWaiter = false;
        lifecycleLock.lock();
        try {
            if (preparationInProgress) {
                preparationWaiter = true;
            } else if (state == State.NEW) {
                preparationInProgress = true;
                preparationOwner = true;
            } else if (state != State.PREPARED && rejectIfNotNeeded) {
                throw illegalTransition("prepare");
            }
        } finally {
            lifecycleLock.unlock();
        }
        if (preparationWaiter) {
            awaitPreparation();
            return;
        }
        if (!preparationOwner) {
            return;
        }

        Throwable preparationFailure = null;
        try {
            prepareGraph();
        } catch (RuntimeException | Error e) {
            preparationFailure = e;
        }

        boolean cleanup = false;
        lifecycleLock.lock();
        try {
            if (preparationFailure == null) {
                if (state == State.NEW) {
                    state = State.PREPARED;
                } else {
                    preparationFailure = new MessagingException("Messaging graph preparation was cancelled in state "
                                                                        + state);
                }
            }
            if (preparationFailure != null && state == State.NEW) {
                cleanup = transitionToFailed(preparationFailure);
            }
        } finally {
            lifecycleLock.unlock();
        }
        if (cleanup) {
            rollback(preparationFailure, false);
        }
        finishPreparation(preparationFailure);
        if (preparationFailure != null) {
            rethrow(preparationFailure);
        }
    }

    @Override
    public void start() {
        prepareIfNeeded(false);

        boolean startOwner;
        lifecycleLock.lock();
        try {
            if (state == State.RUNNING) {
                return;
            }
            if (state == State.STARTING) {
                startOwner = false;
            } else {
                if (state != State.PREPARED) {
                    throw illegalTransition("start");
                }
                state = State.STARTING;
                startOwner = true;
            }
        } finally {
            lifecycleLock.unlock();
        }
        if (!startOwner) {
            awaitStartup();
            return;
        }

        try {
            startOutgoingConnectors();
            for (SourceBinding source : sources.values()) {
                requireStarting();
                source.start(deliveryEngine);
            }
            for (SourceBinding source : sources.values()) {
                requireStarting();
                source.awaitReady();
            }
            lifecycleLock.lock();
            try {
                requireStarting();
                state = State.RUNNING;
                sources.values().forEach(SourceBinding::activate);
                startupCompletion.complete(null);
            } finally {
                lifecycleLock.unlock();
            }
            reportNormalSourceTerminations();
        } catch (RuntimeException | Error e) {
            boolean interrupted = Thread.interrupted();
            try {
                boolean cleanup;
                lifecycleLock.lock();
                try {
                    startupCompletion.completeExceptionally(e);
                    cleanup = state == State.STARTING && transitionToFailed(e);
                } finally {
                    lifecycleLock.unlock();
                }
                if (cleanup) {
                    rollback(e, false);
                } else if (state == State.FAILED) {
                    awaitShutdown();
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            throw e;
        }
    }

    private void startOutgoingConnectors() {
        for (OutgoingConnector outgoing : outgoingConnectors) {
            requireStarting();
            OperationResult result = invokeUnbounded("start outgoing connector "
                                                             + outgoing.getClass().getName(),
                                                     outgoing::start);
            if (result.failure() != null) {
                throw result.failure();
            }
        }
    }

    void ensureRunning() {
        State current = state;
        if (current == State.RUNNING || current == State.DRAINING) {
            return;
        }
        if (current == State.FORCING || current == State.CLOSED || current == State.FAILED) {
            throw illegalTransition("emit through");
        }
        start();
    }

    private void requireEmissionPathOpen() {
        State current = state;
        if (current != State.RUNNING && current != State.DRAINING) {
            throw illegalTransition("emit through");
        }
    }

    void abortPreparation(Throwable cause) {
        Objects.requireNonNull(cause);
        boolean cleanup;
        lifecycleLock.lock();
        try {
            if (state == State.CLOSED || state == State.FAILED) {
                return;
            }
            cleanup = transitionToFailed(cause);
        } finally {
            lifecycleLock.unlock();
        }
        if (cleanup) {
            rollback(cause, false);
        }
    }

    @Override
    public void close() {
        boolean closeOwner;
        boolean graceful;
        lifecycleLock.lock();
        try {
            if (state == State.CLOSED) {
                return;
            }
            if (state == State.RUNNING) {
                deliveryEngine.beginDrain();
                state = State.DRAINING;
                graceful = true;
                closeOwner = shutdownOwner.compareAndSet(false, true);
            } else if (state == State.FAILED || state == State.DRAINING || state == State.FORCING) {
                graceful = false;
                closeOwner = false;
            } else {
                state = State.FORCING;
                graceful = false;
                closeOwner = shutdownOwner.compareAndSet(false, true);
                startupCompletion.completeExceptionally(new MessagingException("Messaging graph startup was cancelled"));
            }
        } finally {
            lifecycleLock.unlock();
        }
        boolean currentGraphTask = isCurrentGraphTask();
        if (!closeOwner) {
            if (!currentGraphTask || shutdownCompletion.isDone()) {
                awaitShutdown();
            }
            return;
        }

        if (currentGraphTask) {
            handOffShutdown(graceful);
            return;
        }

        RuntimeException closeFailure = finishShutdown(graceful);
        if (closeFailure != null) {
            throw closeFailure;
        }
    }

    private RuntimeException finishShutdown(boolean graceful) {
        RuntimeException closeFailure = null;
        try {
            closeFailure = graceful ? closeGracefully() : closeForced(null, "Messaging graph shutdown");
        } catch (RuntimeException e) {
            closeFailure = append(closeFailure, e);
        } catch (Error e) {
            closeFailure = append(closeFailure, new MessagingException("Messaging graph shutdown failed", e));
        } finally {
            lifecycleLock.lock();
            try {
                if (closeFailure == null) {
                    state = State.CLOSED;
                } else {
                    failure = closeFailure;
                    state = State.FAILED;
                }
            } finally {
                lifecycleLock.unlock();
            }
            if (closeFailure == null) {
                shutdownCompletion.complete(null);
            } else {
                shutdownCompletion.completeExceptionally(closeFailure);
            }
        }
        return closeFailure;
    }

    private void handOffShutdown(boolean graceful) {
        RuntimeException handoffFailure;
        try {
            Thread.ofVirtual()
                    .name("helidon-messaging-lifecycle-" + LIFECYCLE_SEQUENCE.incrementAndGet())
                    .inheritInheritableThreadLocals(false)
                    .start(() -> {
                        RuntimeException closeFailure = finishShutdown(graceful);
                        if (closeFailure != null) {
                            LOGGER.log(System.Logger.Level.ERROR,
                                       "Messaging graph shutdown failed after handoff",
                                       closeFailure);
                        }
                    });
            return;
        } catch (RuntimeException e) {
            handoffFailure = new MessagingException("Cannot hand off messaging graph shutdown", e);
        } catch (Error e) {
            handoffFailure = new MessagingException("Cannot hand off messaging graph shutdown", e);
        }
        completeShutdown(handoffFailure);
        throw handoffFailure;
    }

    private boolean isCurrentGraphTask() {
        return lifecycleCallback.get() != null || deliveryEngine.isCurrentDeliveryOrSourceThread();
    }

    private void completeShutdown(RuntimeException closeFailure) {
        lifecycleLock.lock();
        try {
            failure = closeFailure;
            state = State.FAILED;
        } finally {
            lifecycleLock.unlock();
        }
        shutdownCompletion.completeExceptionally(closeFailure);
    }

    private CleanupResult drainSources(RuntimeException current, long deadline) {
        boolean failed = false;
        for (SourceBinding source : sources.values()) {
            if (!source.drainable()) {
                continue;
            }
            OperationResult result = invokeBounded("drain messaging source " + source.name(),
                                                   deadline,
                                                   source::drain);
            current = append(current, result.failure());
            failed |= result.failure() != null;
        }
        return new CleanupResult(current, failed);
    }

    private RuntimeException forceBindings(RuntimeException current,
                                           long deadline,
                                           ForcedCleanupOrdering cleanupOrdering) {
        for (int i = connectorBindings.size() - 1; i >= 0; i--) {
            Connector binding = connectorBindings.get(i);
            CompletableFuture<Void> forceCompletion = cleanupOrdering.register(binding);
            OperationResult result = invokeCleanupBounded("force close connector binding "
                                                                  + binding.getClass().getName(),
                                                          deadline,
                                                          binding::forceClose,
                                                          forceCompletion);
            current = append(current, result.failure());
        }
        return current;
    }

    private RuntimeException closeBindings(RuntimeException current, long deadline) {
        return closeBindings(current, deadline, null);
    }

    private RuntimeException closeBindings(RuntimeException current,
                                           long deadline,
                                           ForcedCleanupOrdering cleanupOrdering) {
        for (int i = connectorBindings.size() - 1; i >= 0; i--) {
            Connector binding = connectorBindings.get(i);
            String operation = "close connector binding " + binding.getClass().getName();
            OperationResult result = cleanupOrdering == null
                    ? invokeCleanupBounded(operation, deadline, binding::close)
                    : invokeCleanupAfterForce(operation,
                                              deadline,
                                              binding::close,
                                              cleanupOrdering.forceCompletion(binding));
            current = append(current, result.failure());
        }
        return current;
    }

    private RuntimeException forceSources(RuntimeException current,
                                          long deadline,
                                          ForcedCleanupOrdering cleanupOrdering) {
        List<SourceBinding> sourceBindings = new ArrayList<>(sources.values());
        for (int i = sourceBindings.size() - 1; i >= 0; i--) {
            Connector connector = sourceBindings.get(i).connector();
            if (connector == null) {
                continue;
            }
            CompletableFuture<Void> forceCompletion = cleanupOrdering.register(connector);
            OperationResult result = invokeCleanupBounded("force close incoming connector "
                                                                  + connector.getClass().getName(),
                                                          deadline,
                                                          connector::forceClose,
                                                          forceCompletion);
            current = append(current, result.failure());
        }
        return current;
    }

    private RuntimeException closeSources(RuntimeException current, long deadline) {
        return closeSources(current, deadline, null);
    }

    private RuntimeException closeSources(RuntimeException current,
                                          long deadline,
                                          ForcedCleanupOrdering cleanupOrdering) {
        List<SourceBinding> sourceBindings = new ArrayList<>(sources.values());
        for (int i = sourceBindings.size() - 1; i >= 0; i--) {
            Connector connector = sourceBindings.get(i).connector();
            if (connector == null) {
                continue;
            }
            String operation = "close incoming connector " + connector.getClass().getName();
            OperationResult result = cleanupOrdering == null
                    ? invokeCleanupBounded(operation, deadline, connector::close)
                    : invokeCleanupAfterForce(operation,
                                              deadline,
                                              connector::close,
                                              cleanupOrdering.forceCompletion(connector));
            current = append(current, result.failure());
        }
        return current;
    }

    private void rollback(Throwable primary, boolean reportFailureOnClose) {
        for (SourceBinding source : sources.values()) {
            source.cancelAdmission();
        }
        long cleanupDeadline = deadline(deliveryEngine.shutdownTimeout());
        ForcedCleanupOrdering cleanupOrdering = new ForcedCleanupOrdering();
        RuntimeException cleanupFailure = forceSources(null, cleanupDeadline, cleanupOrdering);
        deliveryEngine.forceShutdown();
        cleanupFailure = forceBindings(cleanupFailure, cleanupDeadline, cleanupOrdering);
        cleanupFailure = closeSources(cleanupFailure, cleanupDeadline, cleanupOrdering);
        cleanupFailure = closeBindings(cleanupFailure, cleanupDeadline, cleanupOrdering);
        boolean terminated = awaitTermination(cleanupDeadline);
        if (!terminated) {
            cleanupFailure = append(cleanupFailure,
                                    new MessagingException("Messaging startup rollback timed out after "
                                                                   + deliveryEngine.shutdownTimeout()));
        }
        if (cleanupFailure != null && cleanupFailure != primary) {
            primary.addSuppressed(cleanupFailure);
        }
        if (reportFailureOnClose) {
            shutdownCompletion.completeExceptionally(primary);
        } else {
            shutdownCompletion.complete(null);
        }
    }

    private void finalizeTopology() {
        validateTopology();
        Map<String, Integer> routedLimits = new LinkedHashMap<>();
        channels.keySet().forEach(channel -> routedMaxInFlightMessages(channel, routedLimits));
        Map<String, Integer> effectiveLimits = new LinkedHashMap<>();
        channels.keySet().forEach(channel -> effectiveLimits.put(
                channel,
                Math.min(deliveryEngine.maxDeliveryMessages(channel), routedLimits.get(channel))));
        effectiveDeliveryLimits = Map.copyOf(effectiveLimits);
    }

    private int routedMaxInFlightMessages(String channel, Map<String, Integer> routedLimits) {
        Integer existing = routedLimits.get(channel);
        if (existing != null) {
            return existing;
        }
        int limit = deliveryEngine.maxInFlightMessages(channel);
        for (String target : routes.getOrDefault(channel, Set.of())) {
            limit = Math.min(limit, routedMaxInFlightMessages(target, routedLimits));
        }
        routedLimits.put(channel, limit);
        return limit;
    }

    private void validateTopology() {
        for (Map.Entry<String, Set<String>> route : routes.entrySet()) {
            if (!channels.containsKey(route.getKey())) {
                throw new IllegalArgumentException("Unknown messaging route source " + route.getKey());
            }
            for (String target : route.getValue()) {
                if (!channels.containsKey(target)) {
                    throw new IllegalArgumentException("Unknown messaging route target " + target
                                                               + " from " + route.getKey());
                }
            }
        }

        Set<String> visited = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        List<String> path = new ArrayList<>();
        for (String channel : channels.keySet()) {
            visit(channel, visited, visiting, path);
        }
    }

    private void validateContributionSources(Map<String, Runnable> contributionSources) {
        Set<Runnable> newIdentities = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<String, Runnable> entry : contributionSources.entrySet()) {
            String sourceName = Objects.requireNonNull(entry.getKey());
            Runnable source = Objects.requireNonNull(entry.getValue());
            if (sources.containsKey(sourceName)) {
                throw new IllegalArgumentException("Duplicate messaging source " + sourceName);
            }
            if (sourceIdentities.contains(source) || !newIdentities.add(source)) {
                throw new IllegalArgumentException("Messaging source is already owned by this messaging graph");
            }
        }
    }

    private void validateContributionBindings(List<?> bindings,
                                              Iterable<? extends Runnable> contributionSources) {
        Set<Connector> newIdentities = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Runnable source : contributionSources) {
            if (source instanceof Connector connector
                    && (connectorIdentities.contains(connector) || !newIdentities.add(connector))) {
                throw new IllegalArgumentException("Connector is already owned by this messaging graph");
            }
        }
        for (Object binding : bindings) {
            Objects.requireNonNull(binding);
            if (binding instanceof Connector connector
                    && (connectorIdentities.contains(connector) || !newIdentities.add(connector))) {
                throw new IllegalArgumentException("Connector is already owned by this messaging graph");
            }
        }
    }

    private void addSourceLocked(String name, Runnable source) {
        Connector connector = source instanceof Connector candidate ? candidate : null;
        sources.put(name, new SourceBinding(name, source, connector));
        sourceIdentities.add(source);
        if (connector != null) {
            connectorIdentities.add(connector);
        }
    }

    private void addBindingLocked(Object binding) {
        if (binding instanceof Connector connector) {
            connectorIdentities.add(connector);
            connectorBindings.add(connector);
            if (connector instanceof OutgoingConnector outgoingConnector) {
                outgoingConnectors.add(outgoingConnector);
            }
        }
    }

    private void prepareGraph() {
        finalizeTopology();
        requirePreparing();
    }

    private void visit(String channel,
                       Set<String> visited,
                       Set<String> visiting,
                       List<String> path) {
        if (visited.contains(channel)) {
            return;
        }
        if (!visiting.add(channel)) {
            int cycleStart = path.indexOf(channel);
            List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
            cycle.add(channel);
            throw new IllegalArgumentException("Cyclic synchronous messaging route: " + String.join(" -> ", cycle));
        }

        path.add(channel);
        for (String target : routes.getOrDefault(channel, Set.of())) {
            visit(target, visited, visiting, path);
        }
        path.removeLast();
        visiting.remove(channel);
        visited.add(channel);
    }

    private void requireMutable() {
        if (sealed) {
            throw new IllegalStateException("Cannot modify a built messaging graph");
        }
        if (preparationInProgress) {
            throw new IllegalStateException("Cannot modify messaging graph after preparation has started");
        }
        if (state != State.NEW) {
            throw illegalTransition("modify");
        }
    }

    private IllegalStateException illegalTransition(String operation) {
        return new IllegalStateException("Cannot " + operation + " messaging graph in state " + state
                                                 + "; closed and failed graphs cannot be restarted");
    }

    private void sourceFailed(String name, Throwable cause) {
        MessagingException sourceFailure;
        boolean cleanup;
        boolean reportFailureOnClose;
        lifecycleLock.lock();
        try {
            if (state != State.STARTING && state != State.RUNNING) {
                return;
            }
            reportFailureOnClose = state == State.RUNNING;
            sourceFailure = new MessagingException("Messaging source " + name + " failed", cause);
            cleanup = transitionToFailed(sourceFailure);
        } finally {
            lifecycleLock.unlock();
        }
        if (cleanup) {
            rollback(sourceFailure, reportFailureOnClose);
        }
    }

    private void reportNormalSourceTerminations() {
        sources.values().forEach(SourceBinding::reportNormalCompletion);
    }

    private static RuntimeException append(RuntimeException current, RuntimeException additional) {
        if (additional == null) {
            return current;
        }
        if (current == null) {
            return additional;
        }
        if (current == additional) {
            return current;
        }
        current.addSuppressed(additional);
        return current;
    }

    private RuntimeException closeGracefully() {
        RuntimeException closeFailure = null;
        long drainDeadline = deadline(deliveryEngine.shutdownTimeout());
        CleanupResult sourceDrain = drainSources(closeFailure, drainDeadline);
        closeFailure = sourceDrain.failure();
        boolean drained = !sourceDrain.failed() && awaitDrained(drainDeadline);
        closeFailure = collectSourceFailures(closeFailure);
        if (!drained || closeFailure != null) {
            transitionToForcing();
            String message = sourceDrain.failed()
                    ? "Messaging graph source could not be drained; forced shutdown was requested"
                    : !drained
                            ? "Messaging graph drain timed out after " + deliveryEngine.shutdownTimeout()
                                    + "; forced shutdown was requested"
                            : "Messaging source failed while draining; forced shutdown was requested";
            closeFailure = append(closeFailure, new MessagingException(message));
            return closeForced(closeFailure, "Messaging graph forced shutdown");
        }

        long cleanupDeadline = deadline(deliveryEngine.shutdownTimeout());
        closeFailure = closeSources(null, cleanupDeadline);
        if (closeFailure != null) {
            transitionToForcing();
            return closeForced(closeFailure, "Messaging graph connector forced shutdown");
        }
        deliveryEngine.forceShutdown();
        closeFailure = closeBindings(null, cleanupDeadline);
        if (closeFailure != null) {
            transitionToForcing();
            return closeForced(closeFailure, "Messaging graph connector forced shutdown");
        }
        if (!awaitTermination(cleanupDeadline)) {
            closeFailure = new MessagingException("Messaging graph connector shutdown timed out after "
                                                          + deliveryEngine.shutdownTimeout());
            transitionToForcing();
            return closeForced(closeFailure, "Messaging graph connector forced shutdown");
        }
        return closeFailure;
    }

    private RuntimeException closeForced(RuntimeException current, String operation) {
        for (SourceBinding source : sources.values()) {
            source.cancelAdmission();
        }
        long cleanupDeadline = deadline(deliveryEngine.shutdownTimeout());
        ForcedCleanupOrdering cleanupOrdering = new ForcedCleanupOrdering();
        RuntimeException closeFailure = forceSources(current, cleanupDeadline, cleanupOrdering);
        deliveryEngine.forceShutdown();
        closeFailure = forceBindings(closeFailure, cleanupDeadline, cleanupOrdering);
        closeFailure = closeSources(closeFailure, cleanupDeadline, cleanupOrdering);
        closeFailure = closeBindings(closeFailure, cleanupDeadline, cleanupOrdering);
        boolean terminated = awaitTermination(cleanupDeadline);
        if (!terminated) {
            closeFailure = append(closeFailure,
                                  new MessagingException(operation + " timed out after "
                                                                 + deliveryEngine.shutdownTimeout()));
        }
        return closeFailure;
    }

    private RuntimeException collectSourceFailures(RuntimeException current) {
        RuntimeException result = current;
        for (SourceBinding source : sources.values()) {
            Optional<Throwable> sourceFailure = source.takeFailure();
            if (sourceFailure.isEmpty()) {
                continue;
            }
            Throwable cause = sourceFailure.get();
            if (deliveryEngine.ownsShutdownRejection(cause)) {
                continue;
            }
            RuntimeException failure = cause instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new MessagingException("Messaging source " + source.name() + " failed during shutdown", cause);
            result = append(result, failure);
        }
        return result;
    }

    private boolean awaitDrained(long deadline) {
        long remaining = deadline - System.nanoTime();
        return remaining > 0 && deliveryEngine.awaitDrained(Duration.ofNanos(remaining));
    }

    private boolean awaitTermination(long deadline) {
        long remaining = deadline - System.nanoTime();
        return remaining > 0 && deliveryEngine.awaitTermination(Duration.ofNanos(remaining));
    }

    private OperationResult invokeUnbounded(String operation, Runnable action) {
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        Thread thread;
        try {
            thread = Thread.ofVirtual()
                    .name("helidon-messaging-lifecycle-" + LIFECYCLE_SEQUENCE.incrementAndGet())
                    .inheritInheritableThreadLocals(false)
                    .start(() -> {
                        try {
                            invokeLifecycleCallback(action);
                        } catch (Throwable t) {
                            callbackFailure.set(t);
                        } finally {
                            completed.countDown();
                        }
                    });
        } catch (RuntimeException | Error e) {
            return new OperationResult(new MessagingException("Cannot start task to " + operation, e));
        }

        try {
            completed.await();
        } catch (InterruptedException e) {
            thread.interrupt();
            Thread.currentThread().interrupt();
            return new OperationResult(new MessagingException("Interrupted while attempting to " + operation, e));
        }
        return operationResult(operation, callbackFailure.get());
    }

    private OperationResult invokeBounded(String operation, long deadline, Runnable action) {
        return invokeBounded(operation, deadline, action, false, new CompletableFuture<>(), true);
    }

    private OperationResult invokeCleanupBounded(String operation, long deadline, Runnable action) {
        return invokeCleanupBounded(operation, deadline, action, new CompletableFuture<>());
    }

    private OperationResult invokeCleanupBounded(String operation,
                                                 long deadline,
                                                 Runnable action,
                                                 CompletableFuture<Void> callbackCompletion) {
        // Earlier callbacks share this deadline, but must not prevent later owned resources from seeing cleanup.
        return invokeBounded(operation, deadline, action, true, callbackCompletion, true);
    }

    private OperationResult invokeCleanupAfterForce(String operation,
                                                    long deadline,
                                                    Runnable action,
                                                    CompletableFuture<Void> forceCompletion) {
        if (forceCompletion.isDone()) {
            // The forced callback may have consumed the deadline. Keep the caller bounded, but do not interrupt
            // its normal-close callback: a deadline crossing during thread handoff must not make close enter with
            // a stale interrupt inherited from this lifecycle coordinator.
            return invokeBounded(operation, deadline, action, true, new CompletableFuture<>(), false);
        }
        forceCompletion.whenComplete((ignored, ignoredFailure) -> invokeDeferredCleanup(action));
        return new OperationResult(new MessagingException("Timed out while attempting to " + operation));
    }

    private void invokeDeferredCleanup(Runnable action) {
        try {
            Thread.ofVirtual()
                    .name("helidon-messaging-lifecycle-" + LIFECYCLE_SEQUENCE.incrementAndGet())
                    .inheritInheritableThreadLocals(false)
                    .start(() -> {
                        try {
                            invokeLifecycleCallback(action);
                        } catch (Throwable ignored) {
                            // The force timeout already reports that cleanup did not complete within its deadline.
                        }
                    });
        } catch (RuntimeException | Error ignored) {
            // The force timeout already reports that cleanup did not complete within its deadline.
        }
    }

    private OperationResult invokeBounded(String operation,
                                          long deadline,
                                          Runnable action,
                                          boolean attemptAfterDeadline,
                                          CompletableFuture<Void> callbackCompletion,
                                          boolean interruptOnTimeout) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0 && !attemptAfterDeadline) {
            callbackCompletion.complete(null);
            return new OperationResult(new MessagingException("Timed out before attempting to " + operation));
        }
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        Thread thread;
        try {
            thread = Thread.ofVirtual()
                    .name("helidon-messaging-lifecycle-" + LIFECYCLE_SEQUENCE.incrementAndGet())
                    .inheritInheritableThreadLocals(false)
                    .start(() -> {
                        try {
                            invokeLifecycleCallback(action);
                        } catch (Throwable t) {
                            callbackFailure.set(t);
                        } finally {
                            callbackCompletion.complete(null);
                            completed.countDown();
                        }
                    });
        } catch (RuntimeException | Error e) {
            callbackCompletion.complete(null);
            return new OperationResult(new MessagingException("Cannot start task to " + operation, e));
        }

        remaining = deadline - System.nanoTime();
        boolean finished = false;
        if (remaining > 0) {
            try {
                finished = completed.await(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                if (interruptOnTimeout) {
                    thread.interrupt();
                }
                Thread.currentThread().interrupt();
                return new OperationResult(new MessagingException("Interrupted while attempting to " + operation, e));
            }
        }
        if (!finished) {
            if (interruptOnTimeout) {
                thread.interrupt();
            }
            return new OperationResult(new MessagingException("Timed out while attempting to " + operation));
        }

        return operationResult(operation, callbackFailure.get());
    }

    private OperationResult operationResult(String operation, Throwable callbackThrowable) {
        if (callbackThrowable == null) {
            return new OperationResult(null);
        }
        if (callbackThrowable instanceof RuntimeException runtimeException) {
            return new OperationResult(runtimeException);
        }
        return new OperationResult(new MessagingException("Failed to " + operation, callbackThrowable));
    }

    private void invokeLifecycleCallback(Runnable action) {
        lifecycleCallback.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            lifecycleCallback.remove();
        }
    }

    private boolean transitionToFailed(Throwable cause) {
        failure = cause;
        state = State.FAILED;
        startupCompletion.completeExceptionally(cause);
        return shutdownOwner.compareAndSet(false, true);
    }

    private void transitionToForcing() {
        lifecycleLock.lock();
        try {
            state = State.FORCING;
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void requireStarting() {
        if (state != State.STARTING) {
            throw new MessagingException("Messaging graph startup was cancelled in state " + state);
        }
    }

    private void requirePreparing() {
        if (state != State.NEW) {
            throw new MessagingException("Messaging graph preparation was cancelled in state " + state);
        }
    }

    private void awaitStartup() {
        try {
            startupCompletion.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagingException("Interrupted while waiting for messaging graph startup", e);
        } catch (java.util.concurrent.ExecutionException e) {
            awaitFailedStartupCleanup();
            rethrow(e.getCause());
        }
    }

    private void awaitPreparation() {
        try {
            preparationCompletion.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagingException("Interrupted while waiting for messaging graph preparation", e);
        } catch (java.util.concurrent.ExecutionException e) {
            rethrow(e.getCause());
        }
    }

    private void finishPreparation(Throwable preparationFailure) {
        lifecycleLock.lock();
        try {
            preparationInProgress = false;
            if (preparationFailure == null) {
                preparationCompletion.complete(null);
            } else {
                preparationCompletion.completeExceptionally(preparationFailure);
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void awaitFailedStartupCleanup() {
        try {
            shutdownCompletion.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagingException("Interrupted while waiting for failed messaging graph cleanup", e);
        } catch (java.util.concurrent.ExecutionException ignored) {
            // Startup callers consistently observe the startup failure after shutdown cleanup has completed.
        }
    }

    private void awaitShutdown() {
        try {
            shutdownCompletion.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagingException("Interrupted while waiting for messaging graph shutdown", e);
        } catch (java.util.concurrent.ExecutionException e) {
            rethrow(e.getCause());
        }
    }

    private static void rethrow(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new MessagingException("Messaging graph lifecycle failed", throwable);
    }

    private static long deadline(Duration timeout) {
        long now = System.nanoTime();
        long timeoutNanos = timeout.toNanos();
        long result = now + timeoutNanos;
        if (((now ^ result) & (timeoutNanos ^ result)) < 0) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    private static final class ForcedCleanupOrdering {
        private final Map<Connector, CompletableFuture<Void>> forceCompletions = new IdentityHashMap<>();

        private CompletableFuture<Void> register(Connector connector) {
            CompletableFuture<Void> completion = new CompletableFuture<>();
            if (forceCompletions.put(Objects.requireNonNull(connector), completion) != null) {
                throw new IllegalStateException("Connector force cleanup was registered more than once");
            }
            return completion;
        }

        private CompletableFuture<Void> forceCompletion(Connector connector) {
            CompletableFuture<Void> forceCompletion = forceCompletions.get(Objects.requireNonNull(connector));
            if (forceCompletion == null) {
                throw new IllegalStateException("Connector close was registered before force cleanup");
            }
            return forceCompletion;
        }
    }

    private record CleanupResult(RuntimeException failure, boolean failed) {
    }

    private record OperationResult(RuntimeException failure) {
    }

    private record GraphEmitter<T>(DefaultMessagingGraph graph, Emitter<T> delegate) implements Emitter<T> {
        @Override
        public void emit(MessageBatch<? extends T> messages) {
            graph.requireEmissionPathOpen();
            delegate.emit(messages);
        }
    }

    enum State {
        NEW,
        PREPARED,
        STARTING,
        RUNNING,
        DRAINING,
        FORCING,
        CLOSED,
        FAILED
    }

    interface DrainableSource {
        void drain();
    }

    private final class SourceBinding {
        private final String name;
        private final Runnable source;
        private final Connector connector;
        private final IncomingConnector incomingConnector;
        private final ManagedSourceContext incomingContext;
        private final CountDownLatch admissionSignal = new CountDownLatch(1);
        private final AtomicBoolean admissionCancelled = new AtomicBoolean();
        private final AtomicBoolean failureReported = new AtomicBoolean();
        private final AtomicBoolean normalCompletionPending = new AtomicBoolean();
        private DeliveryEngine.SourceTask sourceTask;

        private SourceBinding(String name,
                              Runnable source,
                              Connector connector) {
            this.name = name;
            this.source = source;
            this.connector = connector;
            this.incomingConnector = null;
            this.incomingContext = null;
        }

        private SourceBinding(String name,
                              IncomingConnector incomingConnector,
                              IncomingConnectorContext incomingContext) {
            this.name = name;
            this.source = null;
            this.connector = incomingConnector;
            this.incomingConnector = incomingConnector;
            this.incomingContext = new ManagedSourceContext(incomingContext);
        }

        private String name() {
            return name;
        }

        private Connector connector() {
            return connector;
        }

        private IncomingConnector incomingConnector() {
            return incomingConnector;
        }

        private boolean drainable() {
            return incomingConnector != null || source instanceof DrainableSource;
        }

        private void start(DeliveryEngine deliveryEngine) {
            sourceTask = deliveryEngine.startSource(name, this::run);
            sourceTask.onCompletion(completionFailure -> {
                if (incomingContext != null) {
                    incomingContext.cancel();
                }
                if (completionFailure.isPresent()) {
                    boolean report;
                    lifecycleLock.lock();
                    try {
                        report = state != State.STARTING;
                    } finally {
                        lifecycleLock.unlock();
                    }
                    if (report) {
                        sourceFailed(name, completionFailure.get());
                    }
                } else if (incomingConnector != null) {
                    normalCompletionPending.set(true);
                    reportNormalCompletion();
                }
            });
        }

        private void reportNormalCompletion() {
            boolean report;
            lifecycleLock.lock();
            try {
                report = state == State.RUNNING && normalCompletionPending.compareAndSet(true, false);
            } finally {
                lifecycleLock.unlock();
            }
            if (report) {
                sourceFailed(name, new MessagingException("Managed messaging source stopped unexpectedly"));
            }
        }

        private void awaitReady() {
            if (incomingContext != null && !incomingContext.awaitReady()) {
                sourceTask.failure().ifPresent(SourceBinding::rethrow);
                throw new MessagingException("Managed messaging source stopped before reporting readiness: " + name);
            }
            sourceTask.failure().ifPresent(SourceBinding::rethrow);
        }

        private void activate() {
            if (incomingContext != null) {
                incomingContext.activate();
            } else {
                admissionSignal.countDown();
            }
        }

        private void drain() {
            if (incomingConnector != null) {
                incomingConnector.drain();
            } else {
                ((DrainableSource) source).drain();
            }
        }

        private void cancelAdmission() {
            admissionCancelled.set(true);
            admissionSignal.countDown();
            if (incomingContext != null) {
                incomingContext.cancel();
            }
        }

        private void run() {
            if (incomingConnector != null) {
                incomingConnector.run(incomingContext);
            } else {
                if (!awaitAdmission()) {
                    return;
                }
                source.run();
            }
        }

        private boolean awaitAdmission() {
            try {
                admissionSignal.await();
                return !admissionCancelled.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private Optional<Throwable> takeFailure() {
            if (sourceTask == null) {
                return Optional.empty();
            }
            Optional<Throwable> sourceFailure = sourceTask.failure();
            if (sourceFailure.isEmpty() || !failureReported.compareAndSet(false, true)) {
                return Optional.empty();
            }
            return sourceFailure;
        }

        private static void rethrow(Throwable failure) {
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new MessagingException("Messaging source startup failed", failure);
        }
    }

    private static final class ManagedSourceContext implements IncomingConnectorContext {
        private final IncomingConnectorContext delegate;
        private final CountDownLatch ready = new CountDownLatch(1);
        private final CountDownLatch running = new CountDownLatch(1);
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private ManagedSourceContext(IncomingConnectorContext delegate) {
            this.delegate = Objects.requireNonNull(delegate);
        }

        @Override
        public boolean awaitRunning() {
            ready.countDown();
            try {
                running.await();
                return !cancelled.get() && delegate.awaitRunning();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        @Override
        public String channel() {
            return delegate.channel();
        }

        @Override
        public int maxDeliveryMessages() {
            return delegate.maxDeliveryMessages();
        }

        @Override
        public ConnectorDeliveryReservation reserveDelivery() {
            return delegate.reserveDelivery();
        }

        @Override
        public Optional<ConnectorDeliveryReservation> tryReserveDelivery() {
            return delegate.tryReserveDelivery();
        }

        private boolean awaitReady() {
            try {
                ready.await();
                return !cancelled.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MessagingException("Interrupted while waiting for messaging connector readiness", e);
            }
        }

        private void activate() {
            running.countDown();
        }

        private void cancel() {
            cancelled.set(true);
            ready.countDown();
            running.countDown();
        }
    }
}
