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

package io.helidon.webserver.http;

import java.time.Duration;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;

/**
 * Configuration of a single advertised alternative service.
 */
@Api.Incubating
@Prototype.Blueprint(decorator = AltSvcConfigSupport.BuilderDecorator.class)
@Prototype.Configured(root = false)
interface AltSvcConfigBlueprint extends Prototype.Factory<AltSvc> {
    /**
     * Whether the alternative service should be advertised.
     *
     * @return whether advertisement is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Advertised protocol name.
     * Each Java character in the range {@code U+0000} through {@code U+00FF} maps one-to-one to an opaque protocol
     * identifier byte. When advertisement is enabled, the identifier must contain between {@code 1} and {@code 255}
     * bytes, inclusive. Whitespace-only identifiers are valid.
     *
     * @return advertised protocol
     */
    @Option.Configured
    @Option.Default("h3")
    String protocol();

    /**
     * Advertised port.
     * The port must be between {@code 1} and {@code 65535}, inclusive, when advertisement is enabled.
     * If not configured, the listener port is used.
     *
     * @return advertised port
     */
    @Option.Configured
    Optional<Integer> port();

    /**
     * Advertised maximum age.
     * The maximum age must be non-negative when advertisement is enabled.
     *
     * @return maximum age
     */
    @Option.Configured("max-age")
    Optional<Duration> maxAge();

    /**
     * Whether to emit the {@code persist=1} parameter.
     *
     * @return whether to emit persist
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean persist();
}
