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

import java.util.Objects;

import io.helidon.common.Api;

/**
 * Message admission rejection.
 */
@Api.Preview
public class MessagingRejectedException extends MessagingException {
    /** Rejected channel. */
    private final String channel;
    /** Rejection reason. */
    private final Reason reason;

    /**
     * Create a rejection with a generated diagnostic message.
     *
     * @param channel rejected channel
     * @param reason rejection reason
     */
    public MessagingRejectedException(String channel, Reason reason) {
        this(channel, reason, "Message admission rejected for channel '" + channel + "': " + reason);
    }

    /**
     * Create a rejection.
     *
     * @param channel rejected channel
     * @param reason rejection reason
     * @param message diagnostic message
     */
    public MessagingRejectedException(String channel, Reason reason, String message) {
        super(message);
        this.channel = Objects.requireNonNull(channel, "channel");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Create a rejection with a cause.
     *
     * @param channel rejected channel
     * @param reason rejection reason
     * @param message diagnostic message
     * @param cause root cause
     */
    public MessagingRejectedException(String channel, Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.channel = Objects.requireNonNull(channel, "channel");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Rejected channel.
     *
     * @return channel name
     */
    public String channel() {
        return channel;
    }

    /**
     * Rejection reason.
     *
     * @return typed reason
     */
    public Reason reason() {
        return reason;
    }

    /**
     * Admission rejection reason.
     */
    public enum Reason {
        /**
         * Capacity did not become available before the admission timeout.
         */
        TIMEOUT,

        /**
         * The bounded pending-admission capacity is exhausted, dispatcher contention prevents safely accounting a
         * blocking waiter, or a nested synchronous delivery cannot run immediately without risking a capacity
         * deadlock.
         */
        SATURATED,

        /**
         * The delivery exceeds a configured message-count or connector-reservation limit.
         */
        OVERSIZED,

        /**
         * The messaging runtime is shutting down.
         */
        SHUTDOWN,

        /**
         * Admission was cancelled or interrupted.
         */
        CANCELLED
    }
}
