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

import io.helidon.common.Api;

/**
 * Immutable message routed to a logical dead-letter channel.
 *
 * @param <T> payload type
 */
@Api.Preview
public interface DeadLetterMessage<T> extends Message<T> {
    /**
     * Reserved common header containing the source channel.
     */
    String SOURCE_CHANNEL_HEADER = "helidon_messaging_dead_letter_source_channel";

    /**
     * Reserved common header containing the total delivery attempts.
     */
    String ATTEMPTS_HEADER = "helidon_messaging_dead_letter_attempts";

    /**
     * Reserved common header containing the processing failure class.
     */
    String FAILURE_TYPE_HEADER = "helidon_messaging_dead_letter_failure_type";

    /**
     * Reserved common header containing the processing failure message.
     */
    String FAILURE_MESSAGE_HEADER = "helidon_messaging_dead_letter_failure_message";

    /**
     * Create a dead-letter message.
     *
     * @param originalMessage original message
     * @param sourceChannel source channel
     * @param attempts total delivery attempts
     * @param failure processing failure
     * @param <T> payload type
     * @return immutable dead-letter message
     */
    static <T> DeadLetterMessage<T> create(Message<T> originalMessage,
                                           String sourceChannel,
                                           int attempts,
                                           RuntimeException failure) {
        return new DefaultDeadLetterMessage<>(originalMessage, sourceChannel, attempts, failure);
    }

    /**
     * Original extendable message envelope.
     *
     * @return original message
     */
    Message<T> originalMessage();

    /**
     * Source logical channel.
     *
     * @return source channel
     */
    String sourceChannel();

    /**
     * Total delivery attempts, including the initial delivery.
     *
     * @return delivery attempts
     */
    int attempts();

    /**
     * Processing failure class name.
     *
     * @return failure class name
     */
    String failureType();

    /**
     * Processing failure message, or an empty string when absent.
     *
     * @return failure message
     */
    String failureMessage();
}
