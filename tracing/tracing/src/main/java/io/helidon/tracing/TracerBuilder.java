/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
package io.helidon.tracing;

import java.net.URI;
import java.util.Objects;

import io.helidon.common.Builder;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

import io.opentracing.Tracer;

/**
 * A builder for tracing {@link Tracer tracer}.
 * <p>
 * Each tracer implementation may use different options, though these are the common ones.
 * To keep service implementation abstracted from specific tracer, use {@link #config(Config)}
 * to load configuration of the tracer, as that supports externalization of
 * specific configuration options (e.g. you may use api-version for Zipkin tracer
 * and sampler-type for Jaeger).
 * <p>
 * The following table lists common configuration options that must be honored by each integration (if supported by it).
 * <table class="config">
 *     <caption>Tracer Configuration Options</caption>
 *     <tr>
 *         <th>option</th>
 *         <th>description</th>
 *     </tr>
 *     <tr>
 *         <td>{@code service}</td>
 *         <td>Name of the service sending the tracing information. This is usually visible in the trace data to
 *         distinguish actors in a conversation (e.g. when multiple microservices are connected together)</td>
 *     </tr>
 *     <tr>
 *         <td>{@code protocol}</td>
 *         <td>usually http/https, see {@link #collectorProtocol(String)}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code host}</td>
 *         <td>host of the collector server, defaults are implementation specific, though
 *         often "localhost" as a docker image is expected, see {@link #collectorHost(String)}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code port}</td>
 *         <td>port of the collector server, defaults are implementation specific.
 *         See {@link #collectorPort(int)}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code path}</td>
 *         <td>Path on the collector server used to publish/send span data, defaults are implementation
 *         specific. See {@link #collectorPath(String)}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code tags}</td>
 *         <td>An object config node containing key/value pairs with tag name and string tag value for
 *         tags shared by all spans. See {@link #addTracerTag(String, String)}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code boolean-tags}</td>
 *         <td>An object config node containing key/value pairs with tag name and boolean tag value for
 *         tags shared by all spans. See {@link #addTracerTag(String, boolean)}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code int-tags}</td>
 *         <td>An object config node containing key/value pairs with tag name and integer tag value for
 *         tags shared by all spans. See {@link #addTracerTag(String, Number)}</td>
 *     </tr>
 * </table>
 *
 * <p>
 * Example:
 * <pre>
 * tracing:
 *   # usually must be provided, as it is used as a service identifier
 *   service: "basket-service"
 *   # host of the collector server - example for zipkin in docker environment
 *   #  would use default host, port and path
 *   host: "zipkin"
 *   # example of a tracer-wide tag
 *   tags:
 *      env: "stage-1"
 * </pre>
 *
 * @param <T> type of the builder, used so that inherited builders returned by methods
 *           are of the correct type and contain all methods, even those not inherited from this
 *           interface
 */
@SuppressWarnings("rawtypes")
@Configured(description = "OpenTracing tracer configuration.", ignoreBuildMethod = true)
public interface TracerBuilder<T extends TracerBuilder> extends Builder<Tracer> {
    /**
     * Create a new builder for the service name.
     *
     * @param serviceName name of the service using the tracer
     * @return a new builder instance
     */
    static TracerBuilder<?> create(String serviceName) {
        return TracerProviderHelper.findTracerBuilder()
                .serviceName(serviceName);
    }

    /**
     * Create a new builder from configuration.
     *
     * @param config configuration node to load tracer configuration from
     * @return a new builder instance
     */
    static TracerBuilder<?> create(Config config) {
        return TracerProviderHelper.findTracerBuilder()
                .config(config);
    }

    /**
     * Service name of the traced service.
     *
     * @param name name of the service using the tracer
     * @return updated builder instance
     */
    @ConfiguredOption(key = "service")
    T serviceName(String name);

    /**
     * Set the collector URI used for sending tracing data.
     * <p>
     * Default implementation configures {@link #collectorProtocol(String)},
     * {@link #collectorHost(String)}, {@link #collectorPath(String)} and
     * {@link #collectorPort(int)} if configured in the uri.
     *
     * @param uri the endpoint of the tracing collector
     * @return updated builder instance
     */
    @SuppressWarnings("unchecked")
    default T collectorUri(URI uri) {
        Objects.requireNonNull(uri);

        TracerBuilder<?> result = this;

        if (null != uri.getScheme()) {
            result = result.collectorProtocol(uri.getScheme());
        }

        if (null != uri.getHost()) {
            result = result.collectorHost(uri.getHost());
        }

        if (null != uri.getPath()) {
            result = result.collectorPath(uri.getPath());
        }

        if (uri.getPort() > -1) {
            result = result.collectorPort(uri.getPort());
        }

        return (T) result;
    }

    /**
     * Protocol to use (such as {@code http} or {@code https}) to connect to tracing collector.
     * Default is defined by each tracing integration.
     *
     * @param protocol protocol to use
     * @return updated builder instance
     */
    @ConfiguredOption(key = "protocol")
    T collectorProtocol(String protocol);

    /**
     * Port to use to connect to tracing collector.
     * Default is defined by each tracing integration.
     *
     * @param port port to use
     * @return updated builder instance
     */
    @ConfiguredOption(key = "port")
    T collectorPort(int port);

    /**
     * Host to use to connect to tracing collector.
     * Default is defined by each tracing integration.
     *
     * @param host host to use
     * @return updated builder instance
     */
    @ConfiguredOption(key = "host")
    T collectorHost(String host);

    /**
     * Path on the collector host to use when sending data to tracing collector.
     * Default is defined by each tracing integration.
     *
     * @param path path to use
     * @return updated builder instance
     */
    @ConfiguredOption(key = "path")
    T collectorPath(String path);

    /**
     * Tracer level tags that get added to all reported spans.
     *
     * @param key   name of the tag
     * @param value value of the tag
     * @return updated builder instance
     */
    @ConfiguredOption(key = "tags", kind = ConfiguredOption.Kind.MAP, type = String.class)
    T addTracerTag(String key, String value);

    /**
     * Tracer level tags that get added to all reported spans.
     *
     * @param key   name of the tag
     * @param value numeric value of the tag
     * @return updated builder instance
     */
    @ConfiguredOption(key = "int-tags", kind = ConfiguredOption.Kind.MAP, type = Integer.class)
    T addTracerTag(String key, Number value);

    /**
     * Tracer level tags that get added to all reported spans.
     *
     * @param key   name of the tag
     * @param value boolean value of the tag
     * @return updated builder instance
     */
    @ConfiguredOption(key = "boolean-tags", kind = ConfiguredOption.Kind.MAP, type = Boolean.class)
    T addTracerTag(String key, boolean value);

    /**
     * Load configuration of tracer from configuration of the application.
     * The configuration keys are specific for each tracer integration and documented
     * in these integration projects.
     *
     * @param config configuration node of the tracer configuration
     * @return updated builder instance
     */
    T config(Config config);

    /**
     * When enabled, tracing will be sent. If enabled is false, tracing should
     * use a no-op tracer.
     *
     * @param enabled set to {@code false} to disable distributed tracing
     * @return updated builder instance
     */
    @ConfiguredOption("true")
    T enabled(boolean enabled);

    /**
     * When enabled, the created instance is also registered as a global tracer.
     *
     * @param global whether to register this tracer as a global tracer once built
     * @return updated builder instance
     */
    @ConfiguredOption(key = "global", value = "true")
    T registerGlobal(boolean global);

    /**
     * Build a tracer instance from this builder.
     *
     * @return tracer
     */
    // this method is declared here due to problems with generics
    // declaration on io.helidon.common.Builder
    // this class returned an Object instead of Tracer
    Tracer build();
}
