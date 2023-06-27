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

package io.helidon.common.configurable;

import java.net.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Configuration of a resource.
 */
@Prototype.Blueprint(createEmptyPublic = false, builderInterceptor = ResourceBuilderInterceptor.class)
@Configured
interface ResourceConfigBlueprint extends Prototype.Factory<Resource> {
    /**
     * Resource is located on classpath.
     *
     * @return classpath location of the resource
     */
    @ConfiguredOption
    Optional<String> resourcePath();

    /**
     * Resource is located on filesystem.
     *
     * @return path of the resource
     */
    @ConfiguredOption
    Optional<Path> path();

    /**
     * Plain content of the resource (text).
     *
     * @return plain content
     */
    @ConfiguredOption
    Optional<String> contentPlain();

    /**
     * Binary content of the resource (base64 encoded).
     *
     * @return binary content
     */
    @ConfiguredOption
    Optional<String> content();

    /**
     * Resource is available on a {@link java.net.URI}.
     *
     * @return of the resource
     * @see #proxy()
     * @see #useProxy()
     */
    @ConfiguredOption
    Optional<URI> uri();

    /**
     * Host of the proxy when using URI.
     *
     * @return proxy host
     */
    @ConfiguredOption(builderMethod = false)
    Optional<String> proxyHost();

    /**
     * Port of the proxy when using URI.
     *
     * @return proxy port
     */
    @ConfiguredOption(builderMethod = false, value = "80")
    int proxyPort();

    /**
     * Whether to use proxy. If set to {@code false}, proxy will not be used even if configured.
     * When set to {@code true} (default), proxy will be used if configured.
     *
     * @return whether to use proxy if configured
     */
    @ConfiguredOption(value = "true", builderMethod = false)
    boolean useProxy();

    /**
     * Proxy to use when using uri.
     *
     * @return proxy
     */
    Optional<Proxy> proxy();

    /**
     * Description of this resource when configured through plain text or binary.
     *
     * @return description
     */
    @ConfiguredOption("")
    String description();
}
