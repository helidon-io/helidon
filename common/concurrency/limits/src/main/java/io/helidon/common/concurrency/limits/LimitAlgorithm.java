/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.common.concurrency.limits;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Concurrency limit algorithm.
 * <p>
 * There are two options how to use a limit - by handling a token provided by {@link #tryAcquire()},
 * or by invoking a callable or runnable through one of the invoke methods (such as {@link #invoke(Runnable)}.
 * <p>
 * The invoke methods are backed by the same {@link #tryAcquire()} methods, so behavior is consistent.
 */
public interface LimitAlgorithm {
    /**
     * Invoke a callable within the limits of this limiter.
     * <p>
     * {@link io.helidon.common.concurrency.limits.Limit} implementor's note:
     * Make sure to catch {@link io.helidon.common.concurrency.limits.IgnoreTaskException} from the
     * callable, and call its {@link IgnoreTaskException#handle()} to either return the provided result,
     * or throw the exception after ignoring the timing for future decisions.
     *
     * @param callable callable to execute within the limit
     * @param <T>      the callable return type
     * @return result of the callable
     * @throws LimitException      in case the limiter did not have an available permit
     * @throws java.lang.Exception in case the task failed with an exception
     * @deprecated Use {@link #call(java.util.concurrent.Callable)}.
     */
    @Deprecated(since = "4.3.0", forRemoval = true)
    default <T> T invoke(Callable<T> callable) throws LimitException, Exception {
        Outcome outcome = tryAcquireOutcome();
        if (outcome instanceof Outcome.Accepted accepted) {
            var permit = accepted.token();
            try {
                T response = callable.call();
                permit.success();
                return response;
            } catch (IgnoreTaskException e) {
                permit.ignore();
                return e.handle();
            } catch (Exception e) {
                permit.dropped();
                throw e;
            }
        }
        throw new LimitException("No token available.");
    }

    /**
     * Invoke a callable within the limits of this limiter, invoking the provided {@link java.util.function.Consumer} with the
     * {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome} resulting from applying the limit algorithm.
     * <p>
     * Note that custom implementations of this interface might not implement this method, in which case the caller's limit
     * outcome consumer receives an "unknown" outcome. Clearly the algorithm made a decision, but does not expose it to callers.
     * <p>
     * {@link io.helidon.common.concurrency.limits.Limit} implementor's notes:
     * <ul>
     * <li>Make sure to catch {@link io.helidon.common.concurrency.limits.IgnoreTaskException} from the
     * callable, and call its {@link IgnoreTaskException#handle()} to either return the provided result,
     * or throw the exception after ignoring the timing for future decisions.</li>
     * <li>Make sure the {@code limitOutcomeConsumer} is non-null, and after determining the disposition of the item of work
     * create a suitable {@code LimitOutcome} and pass it to the consumer. Also, make sure to use an outcome-aware token
     * internally so when the caller invokes the token's methods the outcome is updated accordingly.</li>
     * </ul>
     *
     * @param callable                 callable to execute within the limit
     * @param <T>                      the callable return type
     * @return result of the callable with the outcome
     * @throws LimitException      in case the limiter did not have an available permit
     * @throws java.lang.Exception in case the task failed with an exception
     */
    default <T> Result<T> call(Callable<T> callable) throws Exception {
        Outcome outcome = tryAcquireOutcome();
        if (outcome instanceof Outcome.Accepted accepted) {
            var permit = accepted.token();
            try {
                T response = callable.call();
                permit.success();
                return Result.create(response, outcome);
            } catch (IgnoreTaskException e) {
                permit.ignore();
                return Result.create(e.handle(), outcome);
            } catch (Exception e) {
                permit.dropped();
                throw e;
            }
        }
        throw new LimitException("No token available.");
    }

    /**
     * Invoke a runnable within the limits of this limiter.
     * <p>
     * {@link io.helidon.common.concurrency.limits.Limit} implementor's note:
     * Make sure to catch {@link io.helidon.common.concurrency.limits.IgnoreTaskException} from the
     * runnable, and call its {@link IgnoreTaskException#handle()} to either return the provided result,
     * or throw the exception after ignoring the timing for future decisions.
     *
     * @param runnable runnable to execute within the limit
     * @throws LimitException      in case the limiter did not have an available permit
     * @throws java.lang.Exception in case the task failed with an exception
     * @deprecated Use {@link #run(Runnable)}.
     */
    @Deprecated(since = "4.3.0", forRemoval = true)
    default void invoke(Runnable runnable) throws LimitException, Exception {
        run(runnable);
    }

    /**
     * Invoke a runnable within the limits of this limiter, invoking the provided {@link java.util.function.Consumer} with the
     * {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome} resulting from applying the limit algorithm.
     * <p>
     * Note that custom implementations of this interface might not implement this method, in which case the caller's limit
     * outcome consumer receives an "unknown" outcome. Clearly the algorithm made a decision, but does not expose it to callers.
     * <p>
     * {@link io.helidon.common.concurrency.limits.Limit} implementor's notes:
     * <ul>
     * <li>Make sure to catch {@link io.helidon.common.concurrency.limits.IgnoreTaskException} from the
     * runnable, and call its {@link IgnoreTaskException#handle()} to either return the provided result,
     * or throw the exception after ignoring the timing for future decisions.</li>
     * <li>Make sure the {@code limitOutcomeConsumer} is non-null, and after determining the disposition of the item of work
     * create a suitable {@code LimitOutcome} and pass it to the consumer. Also, make sure to use an outcome-aware token
     * internally so when the caller invokes the token's methods the outcome is updated accordingly.</li>
     * </ul>
     *
     * @param runnable             runnable to execute within the limit
     * @return                     {@code Outcome} from the limit algorithm
     * @throws LimitException      in case the limiter did not have an available permit
     * @throws java.lang.Exception in case the task failed with an exception
     */
    default Outcome run(Runnable runnable) throws Exception {
        Outcome outcome = tryAcquireOutcome();
        if (outcome instanceof Outcome.Accepted accepted) {
            Token permit = accepted.token();
            try {
                runnable.run();
                permit.success();
                return outcome;
            } catch (IgnoreTaskException e) {
                permit.ignore();
                e.handle();
                return outcome;
            } catch (Exception e) {
                permit.dropped();
                throw e;
            }
        }
        throw new LimitException("No token available.");
    }

    /**
     * Try to acquire a token, waiting for available permits for the configured amount of time, if queuing is enabled.
     * <p>
     * If acquired, the caller must call one of the {@link io.helidon.common.concurrency.limits.Limit.Token}
     * operations to release the token.
     * If the response is empty, the limit does not have an available token.
     *
     * @return acquired token, or empty if there is no available token
     * @deprecated Use {@link #tryAcquireOutcome()}.
     */
    @Deprecated(since = "4.3.0", forRemoval = true)
    Optional<Token> tryAcquire();

    /**
     * Try to acquire a token, waiting for available permits for the configured amount of time, if queuing is enabled.
     * <p>
     * If acquired, the caller must call one of the {@link io.helidon.common.concurrency.limits.Limit.Token}
     * operations to release the token.
     * If the response is empty, the limit does not have an available token.
     *
     * @return {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome} of the tryAcquire attempt.
     */
    default Outcome tryAcquireOutcome() {
        return tryAcquireOutcome(true);
    }

    /**
     * Try to acquire a token, waiting for available permits for the configured amount of time, if
     * {@code wait} is enabled, returning immediately otherwise.
     * <p>
     * If acquired, the caller must call one of the {@link io.helidon.common.concurrency.limits.Limit.Token}
     * operations to release the token.
     * If the response is empty, the limit does not have an available token.
     *
     * @param wait whether to wait in the queue (if one is configured/available in the limit), or to return immediately
     * @return acquired token, or empty if there is no available token
     * @deprecated Use {@link #tryAcquireOutcome(boolean)}
     */
    @Deprecated(since = "4.3.0", forRemoval = true)
    Optional<Token> tryAcquire(boolean wait);

    /**
     * Tries to acquire a token, waiting for available permits for the configured amount of time, if
     * {@code wait} is enabled, returning immediately otherwise. Concrete implementations should invoke the provided
     * {@code outcomeConsumer}.
     * <p>
     * Note that custom implementations of this interface might not implement this method, in which case the caller's limit
     * outcome consumer receives an "unknown" outcome. Clearly the algorithm made a decision, but does not expose it to callers.
     * <p>
     * If acquired, the caller must call one of the {@link io.helidon.common.concurrency.limits.Limit.Token}
     * operations to release the token.
     * If the response is empty, the limit does not have an available token.
     *
     * @param wait                 whether to wait in the queue (if one is configured/available in the limit), or to
     *                             return immediately
     * @return acquired token, or empty if there is no available token
     */
    default Outcome tryAcquireOutcome(boolean wait) {
        // @Deprecated(since = "4.3.0", forRemoval = true) - For 5.0 remove the default implementation for this method to require
        // implementations to implement it themselves.

        return LimitAlgorithmOutcomeImpl.create(tryAcquire(wait));
    }

    /**
     * When a token is retrieved from {@link #tryAcquire()}, one of its methods must be called when the task
     * is over, to release the token back to the pool (such as a permit returned to a {@link java.util.concurrent.Semaphore}).
     * <p>
     * Choice of method to invoke may influence the algorithm used for determining number of available permits.
     * <p>
     * Implementations of {@code Token} should be updated by their limit implementations with the
     * appropriate {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome} instance so the token can update the outcome
     * and any known listeners depending on which token method is invoked (dropped, ignore, or success).
     */
    interface Token {
        /**
         * Operation was dropped, for example because it hit a timeout, or was rejected by other limits.
         * Loss based {@link io.helidon.common.concurrency.limits.Limit} implementations will likely do an aggressive
         * reducing in limit when this happens.
         */
        void dropped();

        /**
         * The operation failed before any meaningful RTT measurement could be made and should be ignored to not
         * introduce an artificially low RTT.
         */
        void ignore();

        /**
         * Notification that the operation succeeded and internally measured latency should be used as an RTT sample.
         */
        void success();
    }

    /**
     * Represents the outcome of a limit algorithm decision.
     */
    interface Outcome {

        /**
         * Disposition of the work item.
         */
        enum Disposition {

            /**
             * Algorithm accepts the work.
             */
            ACCEPTED,

            /**
             * Algorithm rejects the work due to concurrency limit constratins.
             */
            REJECTED
        }

        /**
         * When the algorithm made its decision relative to the moment when it was asked about a particular work item.
         */
        enum Timing {

            /**
             * Algorithm decided immediately upon being invoked to evaluate whether the caller should process the work item.
             */
            IMMEDIATE,

            /**
             * Algorithm had to wait to decide because too many concurrent executions were already in progress when it was
             * asked about the new work item.
             */
            DEFERRED,

            /**
             * The algorithm decided how to deal with the work item but the caller used a legacy API that does not reveal
             * the {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome} of the decision. As a result,
             * the default implementation cannot find out whether the decision was immediate or deferred.
             */
            @Deprecated(since = "4.3.0", forRemoval = true)
            UNKNOWN
        }

        /**
         * Behavior of an {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome} representing an accepted work
         * item.
         */
        interface Accepted extends Outcome {

            /**
             * The {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Token} the caller should invoke as it
             * processes or rejects the work item.
             *
             * @return the token
             */
            Token token();
        }

        /**
         * Behavior of an {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome} representing a deferred decision.
         */
        interface Deferred extends Outcome {

            /**
             * Start (in system nanos) of the work item's waiting time.
             *
             * @return wait start time (nanoseconds)
             */
            long waitStartNanoTime();

            /**
             * End (in system nanos) of the work item's waiting time.
             *
             * @return wait end (nanoseconds)
             */
            long waitEndNanoTime();
        }

        /**
         * Creates a new {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome} representing an immediate
         * acceptance of a work item.
         *
         * @param originName origin name
         * @param algorithmType algorithm type
         * @param token {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Token}
         * @return immediate acceptance outcome
         */
        static Outcome immediateAcceptance(String originName, String algorithmType, LimitAlgorithm.Token token) {
            return LimitAlgorithmOutcomeImpl.immediateAcceptance(originName, algorithmType, token);
        }

        /**
         * Creates a new {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome} representing a deferred
         * acceptance of a work item.
         *
         * @param originName origin name
         * @param algorithmType algorithm type
         * @param token {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Token}
         * @param waitStartNanos start time of the wait for the decision
         * @param waitEndNanos end time of the wait for the decision
         * @return deferred acceptance outcome
         */
        static Outcome deferredAcceptance(String originName,
                                          String algorithmType,
                                          LimitAlgorithm.Token token,
                                          long waitStartNanos,
                                          long waitEndNanos) {
            return LimitAlgorithmOutcomeImpl.deferredAcceptance(originName, algorithmType, token, waitStartNanos, waitEndNanos);
        }

        /**
         * Creates a new {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome} representing an immediate
         * rejection of a work item.
         * }
         * @param originName origin name
         * @param algorithmType algorithm type
         * @return immediate rejection outcome
         */
        static Outcome immediateRejection(String originName, String algorithmType) {
            return LimitAlgorithmOutcomeImpl.immediateRejection(originName, algorithmType);
        }

        /**
         * Creates a new {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome} representing a deferred
         * rejection of a work item.
         *
         * @param originName origin name
         * @param algorithmType algorithm type
         * @param waitStartNanos start time of the wait for the decision
         * @param waitEndNanos end time of the wait for the decision
         * @return deferred rejection outcome
         */
        static Outcome deferredRejection(String originName, String algorithmType, long waitStartNanos, long waitEndNanos) {
            return LimitAlgorithmOutcomeImpl.deferredRejection(originName, algorithmType, waitStartNanos, waitEndNanos);
        }

        /**
         * Origin (e.g., socket name) of the work item.
         *
         * @return origin
         */
        String originName();

        /**
         * Name of the limit algorithm which decided whether to allow or prevent processing of the work item.
         *
         * @return algorithm name
         */
        String algorithmType();

        /**
         * The {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome.Disposition} of the work item.
         *
         * @return disposition
         */
        Disposition disposition();

        /**
         * The {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome.Timing} of the decision about the
         * work item.
         *
         * @return timing of the decision
         */
        Timing timing();
    }

    /**
     * Carrier for both the result of a {@link java.util.concurrent.Callable} subjected to concurrency limits and the corresponding
     * {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome}.
     *
     * @param <T> type returned by the {@code Callable}
     */
    interface Result<T> {

        /**
         * Creates a new {@code Result} combining the return value from the {@link java.util.concurrent.Callable} and the
         * limit algorithm {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome}.
         *
         * @param callableReturnValue value returned from the {@code Callable}
         * @param outcome limit algorithm outcome
         * @return new {@code Result} combining the return value and the outcome
         * @param <T> type of the return value from the {@code Callable}
         */
        static <T> Result<T> create(T callableReturnValue, Outcome outcome) {
            return LimitAlgorithmResultImpl.create(callableReturnValue, outcome);
        }

        /**
         * Value returned from the {@link java.util.concurrent.Callable} computation.
         *
         * @return callable result
         */
        T result();

        /**
         * {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome} of the limit algorithm.
         *
         * @return outcome
         */
        Outcome outcome();
    }

}
