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

package io.helidon.webserver.observe.log;

import java.time.Duration;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.http.HttpMediaType;

/**
 * Log stream configuration for Log Observer.
 */
@Prototype.Blueprint
@Prototype.Configured
interface LogStreamConfigBlueprint {
    /**
     * Mapper from config to HTTP Media type.
     *
     * @param config config to use
     * @return media type parsed from the config
     */
    @Prototype.FactoryMethod
    static HttpMediaType createContentType(Config config) {
        return config.asString().map(HttpMediaType::create).orElseThrow();
    }

    /**
     * Whether stream is enabled.
     *
     * @return whether to allow streaming of log statements
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    @Option.Configured
    @Option.DefaultCode("@io.helidon.http.HttpMediaTypes@.PLAINTEXT_UTF_8")
    HttpMediaType contentType();

    /**
     * How long to wait before we send the idle message, to make sure we keep the stream alive.
     *
     * @return if no messages appear within this duration, and idle message will be sent
     * @see #idleString()
     */
    @Option.Configured
    @Option.Default("PT5S")
    Duration idleMessageTimeout();

    /**
     * Length of the in-memory queue that buffers log messages from loggers before sending them over the network.
     * If the messages are produced faster than we can send them to client, excess messages are DISCARDED, and will not
     * be sent.
     *
     * @return size of the in-memory queue for log messages
     */
    @Option.Configured
    @Option.DefaultInt(100)
    int queueSize();

    /**
     * String sent when there are no log messages within the {@link #idleMessageTimeout()}.
     *
     * @return string to write over the network when no log messages are received
     */
    @Option.Configured
    @Option.Default("%\\n")
    String idleString();
}
