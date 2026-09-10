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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.faulttolerance.Retry;
import io.helidon.faulttolerance.RetryConfig;
import io.helidon.faulttolerance.RetryContext;
import io.helidon.faulttolerance.RetryException;
import io.helidon.faulttolerance.RetryOutcome;
import io.helidon.faulttolerance.SupplierHelper;

/**
 * Admission, retry, and failure routing for an incoming channel connection.
 */
final class DefaultIncomingConnectorContext implements IncomingConnectorContext {
    private static final System.Logger LOGGER = System.getLogger(DefaultIncomingConnectorContext.class.getName());
    private final DefaultMessagingGraph graph;
    private final DeliveryEngine deliveryEngine;
    private final String channel;
    private final FailurePolicy failurePolicy;
    private final AdmissionTimeoutBudget admissionTimeoutBudget;
    private final Retry retry;

    DefaultIncomingConnectorContext(DefaultMessagingGraph graph, String channel, FailurePolicy failurePolicy) {
        this.graph = graph;
        this.deliveryEngine = graph.deliveryEngine();
        this.channel = channel;
        this.failurePolicy = failurePolicy;
        this.admissionTimeoutBudget = new AdmissionTimeoutBudget(channel, System::nanoTime);
        this.retry = failurePolicy.retry();
    }

    @Override
    public String channel() {
        return channel;
    }

    @Override
    public int maxDeliveryMessages() {
        return graph.maxDeliveryMessages(channel);
    }

    @Override
    public ConnectorDeliveryReservation reserveDelivery() {
        admissionTimeoutBudget.reset();
        ConnectorDeliveryReservation reservation = deliveryEngine.reserveConnectorDelivery(
                channel,
                maxDeliveryMessages(),
                this::processDelivery,
                this::processFailedDelivery);
        // Clear a budget started by a concurrent non-blocking attempt before this reservation succeeded.
        admissionTimeoutBudget.reset();
        return reservation;
    }

    @Override
    public Optional<ConnectorDeliveryReservation> tryReserveDelivery() {
        return admissionTimeoutBudget.attempt(
                () -> deliveryEngine.admissionTimeout(channel)
                        .map(Duration::toNanos)
                        .orElse(Long.MAX_VALUE),
                remaining -> deliveryEngine.tryReserveConnectorDelivery(
                        channel,
                        maxDeliveryMessages(),
                        remaining,
                        this::processDelivery,
                        this::processFailedDelivery));
    }

    private void processFailedDelivery(MessageBatch<?> root, RuntimeException failure) {
        Objects.requireNonNull(failure);
        boolean[] settled = new boolean[root.size()];
        Deque<PendingDelivery> pending = new ArrayDeque<>();
        PendingDelivery current = initialDelivery(root);
        RetryingDelivery retrying = new RetryingDelivery(root);
        recordDeliveryFailure(root,
                              settled,
                              pending,
                              current,
                              retrying,
                              new DeliveryRetryAttempt(1, Duration.ZERO),
                              failure);
        handleTerminalFailure(root, settled, retrying.batch, retrying.failure, 1);
        processPendingDeliveries(root, settled, pending);
    }

    private void processDelivery(MessageBatch<?> root) {
        boolean[] settled = new boolean[root.size()];
        Deque<PendingDelivery> pending = new ArrayDeque<>();
        pending.addFirst(initialDelivery(root));
        processPendingDeliveries(root, settled, pending);
    }

    private PendingDelivery initialDelivery(MessageBatch<?> root) {
        return new PendingDelivery(root,
                                   true,
                                   0,
                                   Duration.ZERO,
                                   System.currentTimeMillis(),
                                   System.nanoTime(),
                                   null);
    }

    private void processPendingDeliveries(MessageBatch<?> root,
                                          boolean[] settled,
                                          Deque<PendingDelivery> pending) {
        while (!pending.isEmpty()) {
            ensureDeliveryActive();
            PendingDelivery current = pending.removeFirst();
            processPendingDelivery(root, settled, pending, current);
        }
    }

    private void processPendingDelivery(MessageBatch<?> root,
                                        boolean[] settled,
                                        Deque<PendingDelivery> pending,
                                        PendingDelivery current) {
        Retry deliveryRetry = retry;
        if (!current.initialDelivery()) {
            Duration remainingTimeout = remainingOverallTimeout(retry.prototype().overallTimeout(),
                                                                current.startedNanos());
            if (current.failedAttempts() > 0 && remainingTimeout.isZero()) {
                handleTerminalFailure(root,
                                      settled,
                                      current.batch(),
                                      Objects.requireNonNull(current.previousFailure()),
                                      current.failedAttempts());
                return;
            }
            deliveryRetry = deferredRetry(current, remainingTimeout);
        }
        RetryingDelivery retrying = new RetryingDelivery(current.batch());
        try {
            deliveryRetry.invoke(context -> {
                if (retrying.terminalFailure == null) {
                    try {
                        attemptDelivery(root, settled, pending, current, retrying, context);
                    } catch (Error | MessagingRejectedException failure) {
                        // Runtime termination must not enter an application's retry classification.
                        retrying.terminalFailure = failure;
                    }
                }
                return Boolean.TRUE;
            }, this::awaitRetry);
        } catch (RetryException failure) {
            if (retrying.terminalFailure == null) {
                handleRetryTermination(root, settled, current, retrying, failure);
            }
        }
        if (retrying.terminalFailure instanceof Error error) {
            throw error;
        }
        if (retrying.terminalFailure instanceof MessagingRejectedException rejection) {
            throw rejection;
        }
    }

    private void attemptDelivery(MessageBatch<?> root,
                                 boolean[] settled,
                                 Deque<PendingDelivery> pending,
                                 PendingDelivery current,
                                 RetryingDelivery retrying,
                                 RetryContext retryContext) {
        ensureDeliveryActive();
        MessageBatch<?> attemptedBatch = retrying.batch;
        try {
            graph.emitBatch(channel, attemptedBatch);
            markSettled(root, settled, attemptedBatch);
            return;
        } catch (RuntimeException failure) {
            recordDeliveryFailure(root,
                                  settled,
                                  pending,
                                  current,
                                  retrying,
                                  new DeliveryRetryAttempt(retryContext.attempt(), retryContext.previousDelay()),
                                  failure);
            throw retryFailure(failure);
        }
    }

    private RuntimeException retryFailure(RuntimeException failure) {
        Throwable current = failure.getCause() instanceof RuntimeException cause ? cause : failure;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current instanceof BatchDeliveryException batchFailure && visited.add(current)) {
            current = batchFailure.getCause();
        }
        if (current instanceof Error error) {
            throw error;
        }
        return SupplierHelper.toRuntimeException(current);
    }

    private void recordDeliveryFailure(MessageBatch<?> root,
                                       boolean[] settled,
                                       Deque<PendingDelivery> pending,
                                       PendingDelivery current,
                                       RetryingDelivery retrying,
                                       DeliveryRetryAttempt retryAttempt,
                                       RuntimeException failure) {
        MessageBatch<?> attemptedBatch = retrying.batch;
        BatchDeliveryException alignedFailure = batchFailure(attemptedBatch, failure);
        List<Integer> failedIndexes = new ArrayList<>();
        List<Integer> deferredIndexes = new ArrayList<>();
        for (int i = 0; i < attemptedBatch.size(); i++) {
            switch (alignedFailure.outcome(i).status()) {
            case SUCCEEDED -> markSettled(root, settled, attemptedBatch, i);
            case NOT_ATTEMPTED -> deferredIndexes.add(i);
            case FAILED, INDETERMINATE -> failedIndexes.add(i);
            default -> throw new IllegalStateException("Unsupported batch item status: "
                                                               + alignedFailure.outcome(i).status());
            }
        }

        if (failedIndexes.isEmpty()) {
            // A completely deferred dispatch still consumes an attempt so a bounded policy cannot spin forever.
            failedIndexes.addAll(deferredIndexes);
            deferredIndexes.clear();
        } else if (!deferredIndexes.isEmpty()) {
            MessageBatch<?> deferredBatch = attemptedBatch.subset(deferredIndexes);
            BatchDeliveryException previousFailure = retrying.failure == null
                    ? null
                    : batchFailure(deferredBatch, retrying.failure);
            pending.addFirst(new PendingDelivery(deferredBatch,
                                                 false,
                                                 totalAttempts(current.failedAttempts(),
                                                               retryAttempt.number() - 1),
                                                 retryAttempt.previousDelay(),
                                                 current.firstCallMillis(),
                                                 current.startedNanos(),
                                                 previousFailure));
        }

        MessageBatch<?> failedBatch = attemptedBatch.subset(failedIndexes);
        BatchDeliveryException policyFailure = batchFailure(failedBatch, alignedFailure);
        retrying.failure(failedBatch, policyFailure);
    }

    private void handleRetryTermination(MessageBatch<?> root,
                                        boolean[] settled,
                                        PendingDelivery current,
                                        RetryingDelivery retrying,
                                        RetryException retryFailure) {
        Throwable lastThrowable = retryFailure.outcome().lastThrowable().orElse(null);
        if (lastThrowable instanceof Error error) {
            throw error;
        }
        MessagingRejectedException rejected = messagingRejection(lastThrowable);
        if (rejected != null) {
            throw rejected;
        }

        RetryOutcome.Termination termination = retryFailure.termination();
        switch (termination) {
        case RETRIES_EXHAUSTED, NOT_RETRYABLE, TIMED_OUT -> {
            BatchDeliveryException failure = Objects.requireNonNull(retrying.failure,
                                                                    "Missing messaging delivery failure");
            int attempts = totalAttempts(current.failedAttempts(), retryFailure.outcome().attempts());
            handleTerminalFailure(root, settled, retrying.batch, failure, attempts);
        }
        case INTERRUPTED, CANCELLED -> throw new MessagingRejectedException(
                channel,
                MessagingRejectedException.Reason.CANCELLED,
                "Messaging delivery retry was cancelled on channel " + channel,
                retryFailure);
        case WAIT_FAILED -> throw new MessagingException(
                "Messaging delivery retry wait failed on channel " + channel,
                retryFailure);
        case RETRY_POLICY_FAILED -> throw new MessagingException(
                "Messaging delivery retry policy failed on channel " + channel,
                retryFailure);
        default -> throw new IllegalStateException("Unsupported retry termination: " + termination);
        }
    }

    private void handleTerminalFailure(MessageBatch<?> root,
                                       boolean[] settled,
                                       MessageBatch<?> failedBatch,
                                       BatchDeliveryException policyFailure,
                                       int failedAttempt) {
        ensureDeliveryActive();
        switch (failurePolicy.onExhausted()) {
        case FAIL -> throw terminalFailure(root,
                                           settled,
                                           failedBatch,
                                           policyFailure,
                                           "Messaging delivery failed after " + failedAttempt
                                                   + " attempt(s) on channel " + channel);
        case DROP -> {
            ensureDeliveryActive();
            LOGGER.log(System.Logger.Level.WARNING,
                       "Messaging delivery failed after " + failedAttempt
                               + " attempt(s); dropping " + failedBatch.size()
                               + " message(s) from channel " + channel,
                       policyFailure);
            markSettled(root, settled, failedBatch);
        }
        case DEAD_LETTER -> routeDeadLetters(root,
                                            settled,
                                            failedBatch,
                                            failedAttempt,
                                            policyFailure);
        default -> throw new IllegalStateException("Unsupported failure disposition: "
                                                           + failurePolicy.onExhausted());
        }
    }

    private Retry deferredRetry(PendingDelivery current, Duration remainingTimeout) {
        // Deferred siblings resume the retained attempt and timeout budgets through the existing internal adapter.
        RetryConfig prototype = retry.prototype();
        if (remainingTimeout.isZero()) {
            return Retry.create(RetryConfig.builder(prototype)
                                        .calls(1)
                                        .overallTimeout(Duration.ofNanos(1))
                                        .buildPrototype());
        }
        Retry.RetryPolicy policy = prototype.retryPolicy().orElseThrow();
        long previousDelayMillis = durationToMillis(current.previousDelay());
        Retry.RetryPolicy offsetPolicy = (_, lastDelay, call) -> policy.nextDelayMillis(
                current.firstCallMillis(),
                call == 1 ? previousDelayMillis : lastDelay,
                totalAttempts(current.failedAttempts(), call));
        return Retry.create(RetryConfig.builder(prototype)
                                    .retryPolicy(offsetPolicy)
                                    .overallTimeout(remainingTimeout)
                                    .buildPrototype());
    }

    private boolean awaitRetry(Duration delay) {
        ensureDeliveryActive();
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagingRejectedException(
                    channel,
                    MessagingRejectedException.Reason.CANCELLED,
                    "Messaging delivery retry was cancelled on channel " + channel,
                    e);
        }
        ensureDeliveryActive();
        return true;
    }

    private int totalAttempts(int completedAttempts, int invocationAttempts) {
        if (completedAttempts > Integer.MAX_VALUE - invocationAttempts) {
            return Integer.MAX_VALUE;
        }
        return completedAttempts + invocationAttempts;
    }

    private long durationToMillis(Duration duration) {
        try {
            return duration.toMillis();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private Duration remainingOverallTimeout(Duration overallTimeout, long startedNanos) {
        long elapsedNanos = Math.max(0, System.nanoTime() - startedNanos);
        long overallTimeoutNanos;
        try {
            overallTimeoutNanos = overallTimeout.toNanos();
        } catch (ArithmeticException e) {
            overallTimeoutNanos = Long.MAX_VALUE;
        }
        return Duration.ofNanos(Math.max(0, overallTimeoutNanos - elapsedNanos));
    }

    private MessagingRejectedException messagingRejection(Throwable throwable) {
        Throwable current = throwable;
        for (int i = 0; current != null && i < 64; i++) {
            if (current instanceof MessagingRejectedException rejected) {
                return rejected;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                return null;
            }
            current = cause;
        }
        return null;
    }

    private void routeDeadLetters(MessageBatch<?> root,
                                  boolean[] settled,
                                  MessageBatch<?> failedBatch,
                                  int failedAttempt,
                                  BatchDeliveryException applicationFailure) {
        String target = failurePolicy.deadLetter().orElseThrow().channel();
        MessageBatch<?> remaining = failedBatch;
        while (true) {
            ensureDeliveryActive();
            MessageBatch<?> deadLetters = deadLetterBatch(remaining, failedAttempt, applicationFailure);
            try {
                graph.emitRoutedBatch(target, deadLetters);
                markSettled(root, settled, remaining);
                return;
            } catch (RuntimeException routeFailure) {
                ensureDeliveryActive();
                BatchDeliveryException alignedRouteFailure = batchFailure(deadLetters, routeFailure);
                List<Integer> succeeded = new ArrayList<>();
                List<Integer> unresolved = new ArrayList<>();
                for (int i = 0; i < remaining.size(); i++) {
                    if (alignedRouteFailure.outcome(i).status() == BatchItemStatus.SUCCEEDED) {
                        succeeded.add(i);
                    } else {
                        unresolved.add(i);
                    }
                }
                if (!succeeded.isEmpty()) {
                    markSettled(root, settled, remaining.subset(succeeded));
                }
                if (!succeeded.isEmpty() && !unresolved.isEmpty()) {
                    remaining = remaining.subset(unresolved);
                    continue;
                }

                MessageBatch<?> unresolvedBatch = unresolved.isEmpty()
                        ? remaining
                        : remaining.subset(unresolved);
                BatchDeliveryException unresolvedFailure = unresolved.isEmpty()
                        ? batchFailure(unresolvedBatch, routeFailure)
                        : batchFailure(unresolvedBatch, alignedRouteFailure);
                BatchDeliveryException result = terminalFailure(
                        root,
                        settled,
                        unresolvedBatch,
                        unresolvedFailure,
                        "Dead-letter delivery from channel " + channel + " to channel " + target + " failed");
                result.addSuppressed(applicationFailure);
                throw result;
            }
        }
    }

    private MessageBatch<?> deadLetterBatch(MessageBatch<?> messages,
                                            int failedAttempt,
                                            RuntimeException failure) {
        List<DeadLetterMessage<Object>> deadLetters = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            deadLetters.add(DeadLetterMessage.create(castMessage(messages.get(i)),
                                                     channel,
                                                     failedAttempt,
                                                     deadLetterFailure(messages, i, failure)));
        }
        return messages.derive(deadLetters);
    }

    @SuppressWarnings("unchecked")
    private Message<Object> castMessage(Message<?> message) {
        return (Message<Object>) message;
    }

    private BatchDeliveryException batchFailure(MessageBatch<?> batch, RuntimeException failure) {
        RuntimeException aligned = BatchDeliveryExceptionSupport.align(batch, failure);
        return aligned instanceof BatchDeliveryException batchFailure
                ? batchFailure
                : BatchDeliveryExceptionSupport.indeterminate("Messaging delivery on channel " + channel,
                                                              batch,
                                                              aligned);
    }

    private BatchDeliveryException terminalFailure(MessageBatch<?> root,
                                                   boolean[] settled,
                                                   MessageBatch<?> unresolved,
                                                   BatchDeliveryException failure,
                                                   String message) {
        List<BatchItemOutcome> outcomes = new ArrayList<>(root.size());
        for (int i = 0; i < root.size(); i++) {
            outcomes.add(settled[i]
                                 ? BatchItemOutcome.succeeded(i)
                                 : BatchItemOutcome.notAttempted(i));
        }
        for (int i = 0; i < unresolved.size(); i++) {
            int rootIndex = unresolved.lineageIndexIn(root, i);
            if (rootIndex < 0) {
                throw new MessagingException("Delivery failure does not belong to retained batch " + root.id());
            }
            BatchItemOutcome outcome = failure.outcome(i);
            outcomes.set(rootIndex, reindex(rootIndex, outcome));
        }
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        BatchDeliveryException result = new BatchDeliveryException(message, cause, root, outcomes);
        for (Throwable suppressed : failure.getSuppressed()) {
            result.addSuppressed(suppressed);
        }
        return result;
    }

    private BatchItemOutcome reindex(int index, BatchItemOutcome outcome) {
        return switch (outcome.status()) {
        case SUCCEEDED -> BatchItemOutcome.succeeded(index);
        case FAILED -> outcome.failure()
                .map(failure -> BatchItemOutcome.failed(index, failure))
                .orElseGet(() -> BatchItemOutcome.failed(index));
        case NOT_ATTEMPTED -> BatchItemOutcome.notAttempted(index);
        case INDETERMINATE -> outcome.failure()
                .map(failure -> BatchItemOutcome.indeterminate(index, failure))
                .orElseGet(() -> BatchItemOutcome.indeterminate(index));
        default -> throw new IllegalStateException("Unsupported batch item status: " + outcome.status());
        };
    }

    private void ensureDeliveryActive() {
        DeliveryEngine.ensureCurrentDeliveryActive();
        if (Thread.currentThread().isInterrupted()) {
            throw new MessagingRejectedException(
                    channel,
                    MessagingRejectedException.Reason.CANCELLED,
                    "Messaging delivery was cancelled on channel " + channel);
        }
    }

    private void markSettled(MessageBatch<?> root, boolean[] settled, MessageBatch<?> batch) {
        if (root.sameDelivery(batch)) {
            Arrays.fill(settled, true);
            return;
        }
        for (int i = 0; i < batch.size(); i++) {
            markSettled(root, settled, batch, i);
        }
    }

    private void markSettled(MessageBatch<?> root,
                             boolean[] settled,
                             MessageBatch<?> batch,
                             int localIndex) {
        int rootIndex = batch.lineageIndexIn(root, localIndex);
        if (rootIndex < 0) {
            throw new MessagingException("Delivery settlement does not belong to retained batch " + root.id());
        }
        settled[rootIndex] = true;
    }

    private RuntimeException deadLetterFailure(MessageBatch<?> batch,
                                               int index,
                                               RuntimeException failure) {
        Set<RuntimeException> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        RuntimeException current = failure;
        while (visited.add(current) && current instanceof BatchDeliveryException batchFailure) {
            int failureIndex = batch.lineageIndexIn(batchFailure.batch(), index);
            if (failureIndex < 0) {
                break;
            }
            Throwable itemFailure = batchFailure.outcome(failureIndex).failure().orElse(batchFailure.getCause());
            if (!(itemFailure instanceof RuntimeException nested)) {
                break;
            }
            current = nested;
        }
        return current;
    }
    private record PendingDelivery(MessageBatch<?> batch,
                                   boolean initialDelivery,
                                   int failedAttempts,
                                   Duration previousDelay,
                                   long firstCallMillis,
                                   long startedNanos,
                                   BatchDeliveryException previousFailure) {
    }

    private record DeliveryRetryAttempt(int number, Duration previousDelay) {
    }

    private static final class RetryingDelivery {
        private MessageBatch<?> batch;
        private BatchDeliveryException failure;
        private Throwable terminalFailure;

        private RetryingDelivery(MessageBatch<?> batch) {
            this.batch = batch;
        }

        private void failure(MessageBatch<?> batch, BatchDeliveryException failure) {
            this.batch = batch;
            this.failure = failure;
        }
    }

}
