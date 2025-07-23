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

import io.helidon.common.concurrency.limits.spi.LimitAlgorithmListener;

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
     * {@link io.helidon.common.concurrency.limits.Limit} implementors note:
     * Make sure to catch {@link io.helidon.common.concurrency.limits.IgnoreTaskException} from the
     * callable, and call its {@link IgnoreTaskException#handle()} to either return the provided result,
     * or throw the exception after ignoring the timing for future decisions.
     *
     * @param callable callable to execute within the limit
     * @param <T>      the callable return type
     * @return result of the callable
     * @throws LimitException      in case the limiter did not have an available permit
     * @throws java.lang.Exception in case the task failed with an exception
     */
    default <T> T invoke(Callable<T> callable) throws LimitException, Exception {
        Optional<Token> token = tryAcquire();
        if (token.isEmpty()) {
            throw new LimitException("No token available.");
        }
        Token permit = token.get();
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

    /**
     * Invoke a callable within the limits of this limiter, notifying provided listeners of the queueing and processing progress
     * of the work.
     * <p>
     * {@link io.helidon.common.concurrency.limits.Limit} implementors notes:
     * <ul>
     *     <li>Be sure to override the default implementation which ignores listeners.</li>
     *     <li>Make sure to catch {@link io.helidon.common.concurrency.limits.IgnoreTaskException} from the
     * callable, and call its {@link IgnoreTaskException#handle()} to either return the provided result,
     * or throw the exception after ignoring the timing for future decisions.</li>
     * </ul>
     *
     * @param callable    callable to execute within the limit
     * @param listeners   listeners to notify of queueing and processing progress
     * @param <T>         the callable return type]
     * @return result of the callable
     * @throws LimitException      in case the limiter did not have an available permit
     * @throws java.lang.Exception in case the task failed with an exception
     */
    default <T> T invoke(Callable<T> callable, Iterable<LimitAlgorithmListener> listeners) throws LimitException, Exception {
        return invoke(callable);
    }

    /**
     * Invoke a runnable within the limits of this limiter.
     * <p>
     * {@link io.helidon.common.concurrency.limits.Limit} implementors note:
     * Make sure to catch {@link io.helidon.common.concurrency.limits.IgnoreTaskException} from the
     * runnable, and call its {@link IgnoreTaskException#handle()} to either return the provided result,
     * or throw the exception after ignoring the timing for future decisions.
     *
     * @param runnable runnable to execute within the limit
     * @throws LimitException      in case the limiter did not have an available permit
     * @throws java.lang.Exception in case the task failed with an exception
     */
    default void invoke(Runnable runnable) throws LimitException, Exception {
        Optional<Token> token = tryAcquire();
        if (token.isEmpty()) {
            throw new LimitException("No token available.");
        }
        Token permit = token.get();
        try {
            runnable.run();
            permit.success();
        } catch (IgnoreTaskException e) {
            permit.ignore();
            e.handle();
        } catch (Exception e) {
            permit.dropped();
            throw e;
        }
    }

    /**
     * Invoke a runnable within the limits of this limiter, notifying provided listeners of the queueing and processing progress
     * of the work.
     * <p>
     * {@link io.helidon.common.concurrency.limits.Limit} implementors note:
     * <ul>
     *     <li>Be sure to override the default implementation which ignores listeners.</li>
     *     <li>Make sure to catch {@link io.helidon.common.concurrency.limits.IgnoreTaskException} from the
     * callable, and call its {@link IgnoreTaskException#handle()} to either return the provided result,
     * or throw the exception after ignoring the timing for future decisions.</li>
     * </ul>
     *
     * @param runnable    runnable to execute within the limit
     * @param listeners   listeners to notify of queueing and processing progress
     * @throws LimitException      in case the limiter did not have an available permit
     * @throws java.lang.Exception in case the task failed with an exception
     */
    default void invoke(Runnable runnable, Iterable<LimitAlgorithmListener> listeners) throws LimitException, Exception {
        invoke(runnable);
    }

    /**
     * Try to acquire a token, waiting for available permits for the configured amount of time, if queuing is enabled.
     * <p>
     * If acquired, the caller must call one of the {@link io.helidon.common.concurrency.limits.Limit.Token}
     * operations to release the token.
     * If the response is empty, the limit does not have an available token.
     *
     * @return acquired token, or empty if there is no available token
     */
    default Optional<Token> tryAcquire() {
        return tryAcquire(true);
    }

    /**
     * Try to acquire a token, waiting for available permits for the configured amount of time if queuing is enabled,
     * notifying provided listeners of the queueing and processing progress of the work.
     * <p>
     * If acquired, the caller must call one of the {@link io.helidon.common.concurrency.limits.Limit.Token}
     * operations to release the token.
     * If the response is empty, the limit does not have an available token.
     *
     * @param listeners   listeners to notify of queueing and processing progress
     * @return acquired token, or empty if there is no available token
     */
    default Optional<Token> tryAcquire(Iterable<LimitAlgorithmListener> listeners) {
        return tryAcquire(true, listeners);
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
     */
    Optional<Token> tryAcquire(boolean wait);

    /**
     * Try to acquire a token, waiting for available permits for the configured amount of time, if
     * {@code wait} is enabled, returning immediately otherwise; the limit algorithm notifies registered listeners as it
     * manages the queueing and execution of the work.
     * <p>
     * {@link io.helidon.common.concurrency.limits.Limit} implementors note:
     * <ul>
     *     <li>Be sure to override the default implementation which ignores listeners.</li>
     * </ul>
     * <p>
     * If acquired, the caller must call one of the {@link io.helidon.common.concurrency.limits.Limit.Token}
     * operations to release the token.
     * If the response is empty, the limit does not have an available token.
     *
     * @param wait      whether to wait in the queue (if one is configured/available in the limit), or to return immediately
     * @param listeners listeners to notify of processing
     *
     * @return acquired token, or empty if there is no available token
     */
    default Optional<Token> tryAcquire(boolean wait, Iterable<LimitAlgorithmListener> listeners) {
        return tryAcquire(wait);
    }

    /**
     * When a token is retrieved from {@link #tryAcquire()}, one of its methods must be called when the task
     * is over, to release the token back to the pool (such as a permit returned to a {@link java.util.concurrent.Semaphore}).
     * <p>
     * Choice of method to invoke may influence the algorithm used for determining number of available permits.
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
}
