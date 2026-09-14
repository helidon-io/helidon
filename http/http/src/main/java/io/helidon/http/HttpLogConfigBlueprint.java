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

package io.helidon.http;

import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration of logging of the HTTP layer.
 * <p>
 * The log level is defined by {@link #loggerName()} + "." + prefix, such as {@code send} or {@code recv}.
 */
@Prototype.Blueprint
@Prototype.Configured
@Prototype.CustomMethods(HttpConfigSupport.HttpConfigCustomMethods.class)
@Prototype.IncludeDefaultMethods
interface HttpLogConfigBlueprint {
    /**
     * Logging of received packets. Uses trace and debug levels on configured logger
     * with suffix of {@code .recv`}.
     *
     * @return {@code true} if logging should be enabled for received packets, {@code false} if no logging should be done
     * @see #loggerName()
     */
    @Option.Configured("recv-log")
    @Option.DefaultBoolean(true)
    boolean receiveLog();

    /**
     * Logging of sent packets. Uses trace and debug levels on configured logger
     * with suffix of {@code .send`}.
     *
     * @return {@code true} if logging should be enabled for sent packets, {@code false} if no logging should be done
     * @see #loggerName()
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean sendLog();

    /**
     * Base name of the logger to use when logging receive and send packets.
     * Either {@code send} or {@code recv} is added as another level depending on log direction.
     *
     * @return logger name
     */
    @Option.Configured
    Optional<String> loggerName();

    /**
     * Header names whose values can be logged at debug level, except sensitive names that are always redacted.
     * All other header values are redacted in protocol logs. Headers such as {@code Authorization}, cookies,
     * and names containing {@code token}, {@code password}, {@code secret}, or {@code key} are never logged even
     * when configured here.
     *
     * @return safe header names for protocol logging
     */
    @Option.Configured("log-safe-headers")
    @Option.Singular
    @Option.DefaultCode("@io.helidon.http.LogFormatter@.defaultSafeHeaderNames()")
    Set<HeaderName> safeHeaders();

    /**
     * Whether TRACE protocol logs can include raw protocol data.
     * <p>
     * This is an unsafe diagnostic option. When enabled together with TRACE logging, protocol logs can include
     * sensitive request or response data such as header values and entity bytes.
     * This option can only be enabled programmatically through the builder.
     * </p>
     *
     * @return whether raw protocol data can be logged at TRACE level
     */
    @Option.DefaultBoolean(false)
    boolean unsafeRawData();
}
