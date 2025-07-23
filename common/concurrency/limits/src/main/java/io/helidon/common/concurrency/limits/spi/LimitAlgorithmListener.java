/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.common.concurrency.limits.spi;

/**
 * Listener to key events as the limit algorithm processes a single instance of incoming work (such as a request).
 * <p>
 * Conceptually, an instance of work that is subject to concurrency limiting goes through two stages:
 * <ol>
 *     <li>Disposition: The outcome can be:
 *     <ul>
 *         <li>immediate acceptance for execution,</li>
 *         <li>immediate rejection due to no capacity, or</li>
 *         <li>queueing for deferred execution or deferred rejection.</li>
*      </ul>
 *     <li>Execution: If the limit algorithm accepts the work, either immediately or after queueing it, the algorithm runs the
 *     work instance.</li>
 * </ol>
 * If the algorithm invokes one of the listener's {@code onAccept} methods, once the work instance completes its execution
 * the algorithm invokes exactly one of the methods {@code onDrop}, {@code onIgnore}, or {@code onSuccess}
 */
public interface LimitAlgorithmListener {

    /**
     * Invoked when the limit algorithm accepts the work instance for immediate execution (without queueing).
     *
     * @param originName name associated with the origin of the work (a socket name, for example)
     * @param limitName name of the limit algorithm in use
     */
    void onAccept(String originName, String limitName);

    /**
     * Invoked when the limit algorithm immediately rejects the work.
     *
     * @param originName name associated with the origin of the work (a socket name, for example)
     * @param limitName name of the limit algorithm in use
     */
    void onReject(String originName, String limitName);

    /**
     * Invoked when the limit algorithm accepts the work after queueing it.
     *
     * @param originName name associated with the origin of the work (a socket name, for example)
     * @param limitName name of the specific limit algorithm in use
     * @param queueingStartTime time in nanoseconds when the work was queued
     * @param queueingEndTime time in nanoseconds when the work was released from the queue
     */
    void onAccept(String originName, String limitName, long queueingStartTime, long queueingEndTime);

    /**
     * Invoked when the limit algorithm rejects the work after queueing it.
     *
     * @param originName name associated with the origin of the work (a socket name, for example)
     * @param limitName name of the specific limit algorithm in use
     * @param queueingStartTime time in nanoseconds when the work was queued
     * @param queueingEndTime time in nanoseconds when the work was released from the queue
     */
    void onReject(String originName, String limitName, long queueingStartTime, long queueingEndTime);

    /**
     * Invoked when the work instance is dropped.
     */
    void onDrop();

    /**
     * Invoked when the work instance is ignored.
     */
    void onIgnore();

    /**
     * Invoked when the work instance's execution succeeds.
     */
    void onSuccess();

}
