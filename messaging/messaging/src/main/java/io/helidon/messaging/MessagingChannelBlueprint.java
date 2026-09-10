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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.common.GenericType;

/**
 * Immutable typed handle identifying a channel in a {@link io.helidon.messaging.MessagingGraph}.
 * <p>
 * A channel handle does not own topology or lifecycle. Register it with a graph and use
 * {@link io.helidon.messaging.MessagingGraph#emitter(io.helidon.messaging.MessagingChannel)} for imperative emission.
 * Close the graph to release its channel resources.
 * The runtime's managed channels also implement this interface.
 *
 * @param <T> payload type
 */
@Api.Preview
@Prototype.Blueprint(decorator = MessagingConfigSupport.ChannelBuilderDecorator.class, createEmptyPublic = false)
@Prototype.CustomMethods(MessagingConfigSupport.ChannelCustomMethods.class)
interface MessagingChannelBlueprint<T> {
    /**
     * Channel name.
     *
     * @return channel name
     */
    @Option.Required
    String name();

    /**
     * Complete channel payload type.
     *
     * @return payload type
     */
    @Option.Required
    GenericType<T> payloadType();
}
