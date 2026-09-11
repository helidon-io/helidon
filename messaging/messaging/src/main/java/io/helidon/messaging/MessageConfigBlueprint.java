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
import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;

/**
 * Message construction options.
 *
 * @param <T> payload type
 */
@Api.Preview
@Prototype.Blueprint(decorator = MessageConfigSupport.BuilderDecorator.class, createEmptyPublic = false)
@Prototype.Sealed
@Prototype.CustomMethods(MessageConfigSupport.class)
interface MessageConfigBlueprint<T> extends Prototype.Factory<Message<T>> {
    /**
     * Non-null message payload.
     *
     * @return payload
     */
    @Option.Confidential
    T entity();

    /**
     * Ordered portable headers. Defaults to an empty list.
     * Setting this option replaces the current entries; adding headers preserves their order and duplicate names.
     * The builder accumulates entries in a mutable list and the prototype contains an immutable snapshot.
     *
     * @return portable headers
     */
    @Option.Confidential
    @Option.Singular("header")
    List<MessageHeader> headers();

    /**
     * Metadata local to this message envelope. Defaults to an empty map.
     * Setting this option replaces the current entries; adding a value replaces the value with the same exact name.
     * The builder accumulates entries in a mutable map and the prototype contains an immutable snapshot.
     * Local metadata remains in-process and is not part of portable headers or generic connector mapping.
     *
     * @return local metadata
     */
    @Option.Confidential
    @Option.Singular(value = "localMetadata", withPrefix = false)
    Map<String, MessageHeaderValue> localMetadata();
}
