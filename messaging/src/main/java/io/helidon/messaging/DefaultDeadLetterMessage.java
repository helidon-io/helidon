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
    private final Message<T> originalMessage;
    private final String sourceChannel;
    private final int attempts;
    private final String failureType;
    private final String failureMessage;
    private final MessageHeaders headers;

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
        this.failureType = actualFailure.getClass().getName();
        this.failureMessage = Objects.toString(actualFailure.getMessage(), "");

        this.headers = MessageHeaders.builder()
                .addAll(originalMessage.headers())
                .set(SOURCE_CHANNEL_HEADER, sourceChannel)
                .set(ATTEMPTS_HEADER, Integer.toString(attempts))
                .set(FAILURE_TYPE_HEADER, failureType)
                .set(FAILURE_MESSAGE_HEADER, failureMessage)
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
    public String failureType() {
        return failureType;
    }

    @Override
    public String failureMessage() {
        return failureMessage;
    }

    @Override
    public T entity() {
        return originalMessage.entity();
    }

    @Override
    public MessageHeaders headers() {
        return headers;
    }
}
