/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.http1;

import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webserver.http1.spi.Http1Upgrader;

/**
 * Configuration of an {@link Http1ConnectionSelector}.
 */
@Prototype.Blueprint
@Prototype.CustomMethods(Http1ConnectionSelectorConfigBlueprint.CustomMethods.class)
interface Http1ConnectionSelectorConfigBlueprint extends Prototype.Factory<Http1ConnectionSelector> {
    /**
     * Upgraders to support upgrading from HTTP/1.1 to a different protocol (such as {@code websocket}).
     *
     * @return map of protocol name to upgrader
     */
    @Option.Singular
    Map<String, Http1Upgrader> upgraders();

    /**
     * HTTP/1 protocol configuration to use for this connection selector.
     *
     * @return HTTP/1 protocol configuration
     */
    Http1Config config();

    final class CustomMethods {
        private CustomMethods() {
        }

        /**
         * Add a new upgrader, replacing an existing one for the same protocol.
         *
         * @param builder builder to update
         * @param upgrader upgrader to add
         */
        @Prototype.BuilderMethod
        static void addUpgrader(Http1ConnectionSelectorConfig.BuilderBase<?, ?> builder, Http1Upgrader upgrader) {
            builder.putUpgrader(upgrader.supportedProtocol(), upgrader);
        }
    }
}
