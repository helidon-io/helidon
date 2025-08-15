/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.discovery.providers.eureka;

import java.time.Duration;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.builder.api.Prototype.Blueprint;
import io.helidon.webclient.http1.Http1Client;

/**
 * Prototypical state for {@link EurekaDiscovery} instances.
 *
 * <p>The {@link EurekaDiscoveryConfig} class is generated at compile time using facilities from the <a
 * href="https://helidon.io/docs/latest/se/builder#_generate_a_class_with_a_builder">Helidon Builder API</a>.</p>
 *
 * <p>Instances of this class are returned by the {@link EurekaDiscovery#prototype()} method.</p>
 *
 * @see EurekaDiscovery
 *
 * @see EurekaDiscovery#prototype()
 *
 * @see EurekaDiscovery#create(EurekaDiscoveryConfig)
 */
@Blueprint
@Prototype.Configured(root = false, value = "eureka")
interface EurekaDiscoveryConfigBlueprint extends Prototype.Factory<EurekaDiscovery> {

    /**
     * Whether to use the discovery cache; {@code true} by default.
     *
     * @return {@code true} if the discovery cache should be used
     */
    @Option.Configured("cache")
    @Option.DefaultBoolean(true)
    boolean cache();

    /**
     * The {@link Http1Client} to use to communicate with the Eureka server.
     *
     * <p>To be useful, the client must have a {@linkplain io.helidon.webclient.http1.Http1ClientConfig prototype} whose
     * {@link io.helidon.webclient.http1.Http1ClientConfig.Builder#baseUri(io.helidon.webclient.api.ClientUri)} property
     * is set to the endpoint of a Eureka Server instance. Often this value will be something like {@code
     * http://example.com:8761/eureka}.</p>
     *
     * @return a {@link Http1Client}
     *
     * @see Http1Client
     *
     * @see io.helidon.webclient.http1.Http1ClientConfig.Builder#baseUri(io.helidon.webclient.api.ClientUri)
     */
    @Option.Configured("client")
    Optional<Http1Client> client();

    /**
     * The name of this {@link EurekaDiscoveryConfig} instance; the default value of {@value EurekaDiscoveryImpl#TYPE}
     * is normally entirely suitable.
     *
     * @return the name of this instance
     *
     * @see io.helidon.common.config.NamedService#name()
     */
    @Option.Default(EurekaDiscoveryImpl.TYPE)
    String name();

    /**
     * Whether the <dfn>host</dfn> component of any {@link java.net.URI URI} should be set to the IP address stored by
     * Eureka, or the hostname; {@code false} by default.
     *
     * @return {@code true} if the IP address should be used; {@code false} if the hostname should be used
     */
    @Option.Configured("prefer-ip-address")
    boolean preferIpAddress();

    /**
     * Whether only changes to service information in the Eureka server should be fetched; {@code true} by default.
     *
     * @return whether only changes to service information in the Eureka server should be fetched
     */
    @Option.Configured("registry-fetch-changes")
    @Option.DefaultBoolean(true)
    boolean registryFetchChanges();

    /**
     * The time between retrievals of service information from the Eureka server; 30 seconds by default.
     *
     * @return the time between retrievals of service information from the Eureka server
     *
     * @see Duration#parse(CharSequence)
     */
    @Option.Configured("registry-fetch-interval")
    @Option.Default("PT30S")
    Duration registryFetchInterval();

    /**
     * The name of the {@link Thread} used to retrieve service information from the Eureka server; "Eureka registry
     * fetch thread" by default.
     *
     * @return the name of the {@link Thread} used to retrieve service information from the Eureka server
     *
     * @see Thread.Builder#name(String)
     */
    @Option.Configured("registry-fetch-thread-name")
    @Option.Default("Eureka registry fetch thread")
    String registryFetchThreadName();

    /**
     * Whether to eagerly start the {@link Thread} that retrieves service information from the Eureka server; {@code
     * true} by default.
     *
     * @return {@code true} if the thread should be started as soon as possible; {@code false} if it should be started
     * as late as possible
     */
    @Option.Configured("registry-fetch-thread-start-eagerly")
    @Option.DefaultBoolean(true)
    boolean registryFetchThreadStartEagerly();

}
