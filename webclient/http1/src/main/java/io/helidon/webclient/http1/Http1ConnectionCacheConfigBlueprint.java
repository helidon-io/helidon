/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.http1;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.spi.LimitProvider;

/**
 * Configuration of the HTTP/1.1 client cache.
 */
@Prototype.Configured
@Prototype.Blueprint
interface Http1ConnectionCacheConfigBlueprint {

    /**
     * Whether to enable connection limits, if they are set.
     * Default value is {@code true}.
     *
     * @return whether to use configured connection limits
     */
    @Option.DefaultBoolean(true)
    @Option.Configured
    boolean enableConnectionLimits();

    /**
     * Total connection limit of the client.
     * This limit cannot be overridden on any underling level.
     * Set as unlimited if not configured.
     *
     * @return configured connection limit
     */
    @Option.Provider(value = LimitProvider.class, discoverServices = false)
    @Option.Configured
    Optional<Limit> connectionLimit();

    /**
     * Limit of how many connections can be created per each host.
     * This limit can be adjusted via specific host configuration.
     *
     * @return configured connection limit
     */
    @Option.Provider(value = LimitProvider.class, discoverServices = false)
    @Option.Configured
    Optional<Limit> connectionPerHostLimit();

    /**
     * Limit of how many connections can be created without proxy.
     *
     * @return configured non-proxy connection limit
     */
    @Option.Provider(value = LimitProvider.class, discoverServices = false)
    @Option.Configured
    Optional<Limit> nonProxyConnectionLimit();

    /**
     * Specific host connection limit configuration.
     * Limit specified for each host will override the one defined by {@link #connectionPerHostLimit()}.
     *
     * @return specific host limits
     */
    @Option.Singular
    @Option.Configured
    List<Http1HostLimitConfig> hostLimits();

    /**
     * Specific proxy limit configurations.
     *
     * @return proxy limit configurations
     */
    @Option.Singular
    @Option.Configured
    List<Http1ProxyLimitConfig> proxyLimits();

    /**
     * Keep alive timeout, how long should the client wait for the connection, when all the connections are taken.
     *
     * @return keep alive connection timeout
     */
    @Option.Configured
    @Option.Default("PT5S")
    Duration keepAliveTimeout();

}
