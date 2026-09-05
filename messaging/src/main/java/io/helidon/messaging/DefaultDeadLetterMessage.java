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

/**
 * Default immutable dead-letter message implementation.
 *
 * @param <T> payload type
 */
final class DefaultDeadLetterMessage<T> implements DeadLetterMessage<T> {
    private static final String NON_PORTABLE_FAILURE_TYPE_HEADER =
            "helidon_messaging_dead_letter_failure_type";
    private static final String NON_PORTABLE_FAILURE_MESSAGE_HEADER =
            "helidon_messaging_dead_letter_failure_message";

    private final Message<T> originalMessage;
    private final String sourceChannel;
    private final int attempts;
    private final MessageHeaders headers;
    private final MessageMetadata localMetadata;

    DefaultDeadLetterMessage(Message<T> originalMessage,
                             String sourceChannel,
                             int attempts,
                             RuntimeException failure) {
        this.originalMessage = Objects.requireNonNull(originalMessage);
        this.sourceChannel = Objects.requireNonNull(sourceChannel);
        if (sourceChannel.isBlank()) {
            throw new IllegalArgumentException("sourceChannel must not be blank");
        }
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be greater than zero");
        }
        this.attempts = attempts;
        RuntimeException actualFailure = Objects.requireNonNull(failure);

        this.headers = MessageHeaders.builder()
                .addAll(originalMessage.headers())
                .remove(NON_PORTABLE_FAILURE_TYPE_HEADER)
                .remove(NON_PORTABLE_FAILURE_MESSAGE_HEADER)
                .set(SOURCE_CHANNEL_HEADER, sourceChannel)
                .set(ATTEMPTS_HEADER, Integer.toString(attempts))
                .build();
        this.localMetadata = MessageMetadata.builder()
                .addAll(originalMessage.localMetadata())
                .set(FAILURE_TYPE_METADATA, actualFailure.getClass().getName())
                .set(FAILURE_MESSAGE_METADATA, Objects.toString(actualFailure.getMessage(), ""))
                .build();
    }

    @Override
    public Message<T> originalMessage() {
        return originalMessage;
    }

    @Override
    public String sourceChannel() {
        return sourceChannel;
    }

    @Override
    public int attempts() {
        return attempts;
    }

    @Override
    public T entity() {
        return originalMessage.entity();
    }

    @Override
    public MessageHeaders headers() {
        return headers;
    }

    @Override
    public MessageMetadata localMetadata() {
        return localMetadata;
    }
}
