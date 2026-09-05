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
 * <p>
 * The failure type and message are local diagnostic data in {@link #localMetadata()}. Connectors must never map local
 * metadata to a transport. Applications that intentionally publish failure diagnostics must first redact and bound
 * them, then explicitly promote them to {@link #headers() portable headers}.
 * <p>
 * Every implementation must provide text values under {@link #FAILURE_TYPE_METADATA} and
 * {@link #FAILURE_MESSAGE_METADATA} in its local metadata.
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
     * Required reserved local metadata containing the processing failure class.
     * <p>
     * Local metadata is never transported implicitly by a connector.
     */
    String FAILURE_TYPE_METADATA = "helidon.messaging.dead-letter.failure.type";

    /**
     * Required reserved local metadata containing the processing failure message.
     * <p>
     * Local metadata is never transported implicitly by a connector.
     */
    String FAILURE_MESSAGE_METADATA = "helidon.messaging.dead-letter.failure.message";

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
     * <p>
     * This local diagnostic is stored under {@link #FAILURE_TYPE_METADATA} in {@link #localMetadata()}. It may expose
     * implementation details and must be explicitly sanitized before publication.
     *
     * @return failure class name
     * @throws IllegalStateException if the required local metadata is missing or is not a text value
     */
    default String failureType() {
        return localMetadata().text(FAILURE_TYPE_METADATA)
                .orElseThrow(() -> new IllegalStateException(
                        "Dead-letter message is missing required local metadata '" + FAILURE_TYPE_METADATA + "'"));
    }

    /**
     * Processing failure message.
     * <p>
     * This local diagnostic is stored under {@link #FAILURE_MESSAGE_METADATA} in {@link #localMetadata()}. It may
     * contain sensitive data and may be arbitrarily long, so it must be explicitly redacted and bounded before
     * publication. A message returned by {@link #create(Message, String, int, RuntimeException)} stores an empty
     * string when the processing exception has no message.
     *
     * @return failure message, or an empty string when the processing exception had no message
     * @throws IllegalStateException if the required local metadata is missing or is not a text value
     */
    default String failureMessage() {
        return localMetadata().text(FAILURE_MESSAGE_METADATA)
                .orElseThrow(() -> new IllegalStateException(
                        "Dead-letter message is missing required local metadata '" + FAILURE_MESSAGE_METADATA + "'"));
    }
}
