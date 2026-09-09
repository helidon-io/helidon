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

import java.util.UUID;

import io.helidon.builder.api.Prototype;

final class MessageBatchConfigSupport {
    private MessageBatchConfigSupport() {
    }

    static String defaultId() {
        return UUID.randomUUID().toString();
    }

    // The explicit non-option name keeps this top-level generic factory from being offered as an option conversion.
    @Prototype.RuntimeTypeFactoryMethod("messageBatch")
    static <T> MessageBatch<T> create(MessageBatchConfig<T> config) {
        return MessageBatch.create(config);
    }

    static final class IdDecorator
            implements Prototype.OptionDecorator<MessageBatchConfig.BuilderBase<?, ?, ?>, String> {
        @Override
        public void decorate(MessageBatchConfig.BuilderBase<?, ?, ?> target, String id) {
            MessageBatch.validateId(id);
        }
    }

    static final class BuilderDecorator
            implements Prototype.BuilderDecorator<MessageBatchConfig.BuilderBase<?, ?, ?>> {
        @Override
        public void decorate(MessageBatchConfig.BuilderBase<?, ?, ?> target) {
            MessageBatch.validateId(target.id());
            if (target.messages().isEmpty()) {
                throw new IllegalArgumentException("Message batch must contain at least one message");
            }
        }
    }
}
