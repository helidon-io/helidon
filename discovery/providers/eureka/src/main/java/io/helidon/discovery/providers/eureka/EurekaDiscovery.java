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

import java.net.URI;
import java.util.SequencedSet;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType.Api;
import io.helidon.builder.api.RuntimeType.PrototypedBy;
import io.helidon.discovery.DiscoveredUri;
import io.helidon.discovery.Discovery;
import io.helidon.discovery.providers.eureka.EurekaDiscoveryConfig.Builder;

/**
 * A {@link Discovery} implementation that communicates with <a
 * href="https://github.com/Netflix/eureka/tree/v2.0.5">Netflix's Eureka server version 2</a>.
 *
 * <p>Please see the {@link Discovery} documentation for important contractual obligations that must be fulfilled by any
 * implementation of this interface.</p>
 *
 * <h2>Configuration</h2>
 *
 * <p>Configuration for {@link EurekaDiscoveryConfig} instances is normally found under the top-level Helidon
 * configuration key named {@code discovery}, under a sub-key named {@code eureka}. Please see <a
 * href="https://helidon.io/docs/latest/config/io_helidon_discovery_providers_eureka_EurekaDiscovery">the configuration
 * reference</a> for more details.</p>
 *
 * <p>Configuration for connecting to a Eureka server hosted on {@code localhost}, port {@code 8761} (the port used by
 * Eureka by default), looks like this (in YAML format, often in an {@code application.yaml} classpath resource):</p>
 *
 * {@snippet lang="yaml" :
 *   discovery:
 *     eureka:
 *       client:
 *         base-uri: "http://localhost:8761/eureka"
 *       enabled: true # true by default; made explicit here for example purposes
 *   }
 *
 * <h2>Acquisition ("Declarative" Usage)</h2>
 *
 * <p>To acquire a {@link Discovery} instance, <a
 * href="https://helidon.io/docs/latest/se/injection#_injection_points">inject</a> it, using the {@link
 * io.helidon.service.registry.Service.Inject} annotation. Alternatively, use the {@code static} {@link
 * io.helidon.service.registry.Services#get(Class)} method, supplying {@link Discovery Discovery}{@code .class} as its
 * sole argument.</p>
 *
 * <h2>Creation ("Imperative" Usage)</h2>
 *
 * <p>Users of the <a href="https://helidon.io/docs/latest/se/builder#_example_2">Helidon Builder API</a> may create
 * instances of this interface via the {@link #create(EurekaDiscoveryConfig)} method. Instances created in this manner
 * are guaranteed to implement all of the contractual requirements of this interface and its (transitive)
 * supertypes.</p>
 *
 * <p>(Users of the <a href="https://helidon.io/docs/latest/se/builder#_example_3">Helidon Builder API</a> may
 * indirectly create instances of the {@link EurekaDiscoveryConfig} interface, if required, via the {@link #builder()}
 * method, which is a Helidon Builder API-mandated convenience method that, by specification, simply delegates to the
 * {@link EurekaDiscoveryConfig#builder()} method. See {@link EurekaDiscoveryConfig#builder()} for contractual details
 * related to the creation of {@link EurekaDiscoveryConfig.Builder} instances. See {@link EurekaDiscoveryConfig} for
 * contractual details related to {@link EurekaDiscoveryConfig} instances {@linkplain
 * EurekaDiscoveryConfig.Builder#build() built} using {@link EurekaDiscoveryConfig.Builder} instances. These contracts
 * do not affect the requirements this interface imposes on its implementations.)</p>
 *
 * <p>Any other means of implementing this interface is permitted, so long as it results in an implementation that
 * implements the contractual requirements of this interface's (transitive and reflexive) supertypes.</p>
 *
 * <h2>Logging</h2>
 *
 * <p>Implementations created via the {@link #create(EurekaDiscoveryConfig)} method (which includes instances available
 * via <a href="https://helidon.io/docs/latest/se/injection#_injection_points">dependency injection</a>), and any
 * internal supporting classes, will use {@link System.Logger Logger}s {@linkplain System#getLogger(String) whose names
 * begin with} {@code io.helidon.discovery.providers.eureka.}.</p>
 *
 * <p>Logging output is particularly important to monitor because as a general rule {@link Discovery} implementations
 * must strive to be resilient in the presence of failures.</p>
 *
 * <p><strong>Design note:</strong> This interface extends {@link Api Api&lt;EurekaDiscoveryConfig&gt;} to conform to
 * the requirements of the <a href="https://helidon.io/docs/latest/se/builder#_creating_a_runtime_type">Helidon Builder
 * API</a>. Implementations of this interface must therefore also abide by {@linkplain Api its contract}.</p>
 *
 * @see #uris(String, URI)
 *
 * @see #close()
 *
 * @see #create(EurekaDiscoveryConfig)
 *
 * @see #builder()
 *
 * @see EurekaDiscoveryConfig#builder()
 *
 * @see EurekaDiscoveryConfig.Builder#build()
 *
 * @see EurekaDiscoveryConfig
 *
 * @see Api
 *
 * @see <a href="https://helidon.io/docs/latest/se/builder">Helidon Builder API</a>
 *
 * @see <a href="https://helidon.io/docs/latest/config/io_helidon_discovery_providers_eureka_EurekaDiscovery">Helidon
 * Configuration Reference</a>
 */
@PrototypedBy(EurekaDiscoveryConfig.class)
public interface EurekaDiscovery extends Api<EurekaDiscoveryConfig>, Discovery {


    /*
     * Instance methods.
     */


    /**
     * Closes any resources used by this {@link EurekaDiscovery} implementation.
     *
     * <p><a href="https://github.com/Netflix/eureka/tree/v2.0.5">Eureka</a> is a server. Its existence implies network
     * communication. Consequently {@link EurekaDiscovery} implementations are required to implement this method, as
     * appropriate, such that it cleans up network-related resources (such as HTTP connections).</p>
     *
     * <p>The behavior of any given {@link EurekaDiscovery} implementation is undefined, unless otherwise documented by
     * the specific implementation, after an invocation of its {@link #close()} method has taken place. For maximum
     * portability, callers should assume that closing a {@link EurekaDiscovery} instance transitions it into a terminal
     * state.</p>
     *
     * @see Discovery#close()
     *
     * @exception RuntimeException if an error occurs
     */
    @Override // Discovery (AutoCloseable)
    void close();

    /**
     * Returns a non-{@code null}, determinate {@link EurekaDiscoveryConfig} object representing the <dfn>prototypical
     * state</dfn> of this {@link EurekaDiscovery} implementation.
     *
     * @return a non-{@code null}, determinate {@link EurekaDiscoveryConfig} object representing the <dfn>prototypical
     * state</dfn> of this {@link EurekaDiscovery} implementation
     *
     * @see EurekaDiscoveryConfig
     */
    @Override // Api<EurekaDiscoveryConfig>
    EurekaDiscoveryConfig prototype();

    /**
     * Returns a non-{@code null}, immutable, non-{@linkplain SequencedSet#isEmpty() empty}, {@link SequencedSet} of
     * {@link DiscoveredUri} instances suitable for the supplied <dfn>discovery name</dfn>.
     *
     * <p>See the documentation for the {@link Discovery#uris(String, URI)} method for more contractual
     * information. Documentation that follows is specific to all {@link EurekaDiscovery} implementations. Any given
     * {@link EurekaDiscovery} implementation may further refine these requirements.</p>
     *
     * <p>Implementations of this method often serve cached information. The cache is typically refreshed by a
     * background thread on a regular interval. See <a href="EurekaDiscoveryConfig.html#cache()">{@code
     * EurekaDiscoveryConfig.cache()}</a> and <a href="EurekaDiscoveryConfig.html#registryFetchInterval()">{@code
     * EurekaDiscoveryConfig.registryFetchInterval()}</a> and their related methods.</p>
     *
     * <p>Implementations of this method <em>must</em> treat discovery names in a case-insensitive manner.</p>
     *
     * <p>Implementations of this method <em>must</em> order elements in the returned {@link SequencedSet} with elements
     * that are known at invocation time to be <a
     * href="https://javadoc.io/static/com.netflix.eureka/eureka-client/2.0.5/com/netflix/appinfo/InstanceInfo.InstanceStatus.html#UP">{@code
     * UP}</a> or <a
     * href="https://javadoc.io/static/com.netflix.eureka/eureka-client/2.0.5/com/netflix/appinfo/InstanceInfo.InstanceStatus.html#STARTING">{@code
     * STARTING}</a> preceding other elements. All other ordering is undefined.</p>
     *
     * @param discoveryName a discovery name; must not be {@code null}; will be handled in a case-insensitive manner;
     * must comply with any requirements that might exist pertaining to a Eureka <a
     * href="https://javadoc.io/static/com.netflix.eureka/eureka-client/2.0.5/com/netflix/discovery/shared/Application.html#getName()"><dfn>application
     * name</dfn></a>
     *
     * @param defaultValue a {@link URI} that will be {@linkplain DiscoveredUri#uri() represented} as a {@link
     * DiscoveredUri} with no metdata if any kind of error occurs that prevents a normal invocation, or if the return value of an
     * invocation of this method would otherwise be empty; must not be {@code null}; will always be represented as a
     * {@link DiscoveredUri} and included as the {@linkplain SequencedSet#getLast() last element} in the returned {@link
     * SequencedSet}
     *
     * @return a non-{@code null}, immutable, non-{@linkplain SequencedSet#isEmpty() empty}, {@link SequencedSet} of
     * {@link DiscoveredUri} instances
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @exception IllegalArgumentException if any argument is unsuitable in any way
     *
     * @see Discovery#uris(String, URI)
     *
     * @see #close()
     *
     * @see <a href="EurekaDiscoveryConfig.html#cache()">{@code EurekaDiscoveryConfig.cache()}</a>
     *
     * @see DiscoveredUri
     */
    @Override // Discovery
    SequencedSet<DiscoveredUri> uris(String discoveryName, URI defaultValue);


    /*
     * Static methods.
     */


    /**
     * A convenience method required by, and complying with the requirements of, the <a
     * href="https://helidon.io/docs/latest/se/builder#_example_3">Helidon Builder API</a> that, per contract, invokes
     * the {@link EurekaDiscoveryConfig#builder()} method and returns its result.
     *
     * @return a non-{@code null} {@link Builder} of {@link EurekaDiscoveryConfig} instances
     *
     * @see EurekaDiscoveryConfig#builder()
     *
     * @see EurekaDiscoveryConfig
     *
     * @see Builder
     *
     * @see <a href="https://helidon.io/docs/latest/se/builder#_example_3">Helidon Builder API</a>
     */
    // Required by Helidon Builder API
    static Builder builder() {
        return EurekaDiscoveryConfig.builder();
    }

    /**
     * Required by, and complying with the requirements of, the <a
     * href="https://helidon.io/docs/latest/se/builder#_example_3">Helidon Builder API</a>, creates a new {@link
     * EurekaDiscovery} implementation whose state is derived from the supplied {@link EurekaDiscoveryConfig} and
     * returns it.
     *
     * @param prototype the {@link EurekaDiscoveryConfig} from which the implementation will be created; must not be
     * {@code null}
     *
     * @return a new, non-{@code null} {@link EurekaDiscovery} implementation
     *
     * @exception NullPointerException if {@code prototype} is {@code null}
     *
     * @see #prototype()
     *
     * @see <a href="https://helidon.io/docs/latest/se/builder#_example_3">Helidon Builder API</a>
     */
    // Required by Helidon Builder API
    static EurekaDiscovery create(EurekaDiscoveryConfig prototype) {
        return new EurekaDiscoveryImpl(prototype);
    }

    /**
     * A convenience method required by, and complying with the requirements of, the <a
     * href="https://helidon.io/docs/latest/se/builder#_example_2">Helidon Builder API</a> that, per contract,
     * {@linkplain Builder#build() builds a new} {@link EurekaDiscovery} implementation and returns it.
     *
     * @param consumer a {@link Consumer} of {@link Builder} instances that typically mutates its supplied {@link
     * Builder}; must not be {@code null}
     *
     * @return a new, non-{@code null} {@link EurekaDiscovery} implementation as returned by the {@link Builder#build()}
     * method
     *
     * @exception NullPointerException if {@code consumer} is {@code null}
     *
     * @see Builder#update(Consumer)
     *
     * @see Builder#build()
     *
     * @see <a href="https://helidon.io/docs/latest/se/builder#_example_2">Helidon Builder API</a>
     */
    // Required by Helidon Builder API
    static EurekaDiscovery create(Consumer<Builder> consumer) {
        return builder().update(consumer).build();
    }

}
