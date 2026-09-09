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

package io.helidon.messaging.tests.custom.connector;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Channel-specific custom connector configuration.
 */
@Prototype.Blueprint(isPublic = false)
@Prototype.Configured(root = false)
interface CustomChannelConfigBlueprint {
    /**
     * Logical messaging channel name.
     *
     * @return channel name
     */
    @Option.Configured
    String channelName();

    /**
     * In-memory endpoint override, using the connector endpoint when absent.
     *
     * @return endpoint override
     */
    @Option.Configured
    Optional<String> endpoint();

    /**
     * Message prefix override, using the connector prefix when absent.
     *
     * @return prefix override
     */
    @Option.Configured
    Optional<String> prefix();
}
