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
 * Default immutable message implementation.
 *
 * @param <T> payload type
 */
final class DefaultMessage<T> implements Message<T> {
    private final T entity;
    private final MessageHeaders headers;
    private final MessageMetadata localMetadata;

    DefaultMessage(T entity, MessageHeaders headers) {
        this(entity, headers, MessageMetadata.empty());
    }

    DefaultMessage(T entity, MessageHeaders headers, MessageMetadata localMetadata) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.headers = Objects.requireNonNull(headers);
        this.localMetadata = Objects.requireNonNull(localMetadata);
    }

    @Override
    public T entity() {
        return entity;
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
