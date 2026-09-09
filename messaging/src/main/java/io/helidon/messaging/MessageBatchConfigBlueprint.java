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

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;

/**
 * Message batch construction options.
 *
 * @param <T> payload type
 */
@Api.Preview
@Prototype.Blueprint(decorator = MessageBatchConfigSupport.BuilderDecorator.class, createEmptyPublic = false)
@Prototype.CustomMethods(MessageBatchConfigSupport.class)
interface MessageBatchConfigBlueprint<T> extends Prototype.Factory<MessageBatch<T>> {
    /**
     * Opaque delivery correlation ID.
     * Defaults to a random UUID generated for each builder.
     *
     * @return identity
     */
    @Option.DefaultMethod(type = MessageBatchConfigSupport.class, value = "defaultId")
    @Option.Decorator(MessageBatchConfigSupport.IdDecorator.class)
    String id();

    /**
     * Ordered messages.
     * The builder's {@code messages} method replaces the current list, while {@code add} and {@code addMessages} append.
     *
     * @return messages
     */
    @Option.Confidential
    @Option.Singular(value = "add", withPrefix = false)
    List<Message<T>> messages();
}
