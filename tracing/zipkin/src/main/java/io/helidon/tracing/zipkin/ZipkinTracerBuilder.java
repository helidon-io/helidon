/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.tracing.zipkin;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import io.helidon.common.HelidonFeatures;
import io.helidon.config.Config;
import io.helidon.tracing.Tag;
import io.helidon.tracing.TracerBuilder;

import brave.Tracing;
import brave.opentracing.BraveTracer;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.util.GlobalTracer;
import zipkin2.Span;
import zipkin2.codec.BytesEncoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.Sender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * The ZipkinTracerBuilder is a convenience builder for  {@link Tracer} to use with Zipkin.
 * <p>
 * <b>Unless You want to explicitly depend on Zipkin in Your code, please
 * use {@link TracerBuilder#create(String)} or {@link TracerBuilder#create(Config)} that is abstracted.</b>
 * <p>
 * The following table lists zipkin specific defaults and configuration options.
 * <table class="config">
 *     <caption>Tracer Configuration Options</caption>
 *     <tr>
 *         <th>option</th>
 *         <th>default</th>
 *         <th>description</th>
 *     </tr>
 *     <tr>
 *         <td>{@code api-version}</td>
 *         <td>2</td>
 *         <td>Version of the Zipkin API to use, currently supports 1 and 2</td>
 *     </tr>
 *     <tr>
 *         <td>{@code service}</td>
 *         <td>&nbsp;</td>
 *         <td>Required service name</td>
 *     </tr>
 *     <tr>
 *         <td>{@code protocol}</td>
 *         <td>{@code http}</td>
 *         <td>see {@link TracerBuilder}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code host}</td>
 *         <td>{@code 127.0.0.1}</td>
 *         <td>see {@link TracerBuilder}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code port}</td>
 *         <td>9411</td>
 *         <td>see {@link TracerBuilder}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code path}</td>
 *         <td>{@code /api/v2/spans}</td>
 *         <td>Default for {@link Version#V2}, which is the default version</td>
 *     </tr>
 *     <tr>
 *         <td>{@code tags}</td>
 *         <td>&nbsp;</td>
 *         <td>see {@link TracerBuilder}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code boolean-tags}</td>
 *         <td>&nbsp;</td>
 *         <td>see {@link TracerBuilder}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code int-tags}</td>
 *         <td>&nbsp;</td>
 *         <td>see {@link TracerBuilder}</td>
 *     </tr>
 * </table>
 *
 * @see <a href="http://zipkin.io/pages/instrumenting.html#core-data-structures">Zipkin Attributes</a>
 * @see <a href="https://github.com/openzipkin/zipkin/issues/962">Zipkin Missing Service Name</a>
 */
public class ZipkinTracerBuilder implements TracerBuilder<ZipkinTracerBuilder> {
    static final Logger LOGGER = Logger.getLogger(ZipkinTracerBuilder.class.getName());
    static final String DEFAULT_PROTOCOL = "http";
    static final int DEFAULT_ZIPKIN_PORT = 9411;
    static final String DEFAULT_ZIPKIN_HOST = "127.0.0.1";
    static final Version DEFAULT_VERSION = Version.V2;
    static final boolean DEFAULT_ENABLED = true;

    static {
        HelidonFeatures.register("Tracing", "Zipkin");
    }

    private final List<Tag<?>> tags = new LinkedList<>();
    private String serviceName;
    private String protocol = DEFAULT_PROTOCOL;
    private String host = DEFAULT_ZIPKIN_HOST;
    private int port = DEFAULT_ZIPKIN_PORT;
    private String path;
    private Version version = DEFAULT_VERSION;
    private Sender sender;
    private String userInfo;
    private boolean enabled = DEFAULT_ENABLED;
    private boolean global = true;

    /**
     * Default constructor, does not modify state.
     */
    protected ZipkinTracerBuilder() {
    }

    /**
     * Get a Zipkin {@link Tracer} builder for processing tracing data of a service with a given name.
     *
     * @param serviceName name of the service that will be using the tracer.
     * @return {@code Tracer} builder for Zipkin.
     */
    public static ZipkinTracerBuilder forService(String serviceName) {
        return create()
                .serviceName(serviceName);
    }

    /**
     * Create a new builder based on values in configuration.
     * This requires at least a key "service" in the provided config.
     *
     * @param config configuration to load this builder from
     * @return a new builder instance.
     * @see ZipkinTracerBuilder#config(Config)
     */
    public static ZipkinTracerBuilder create(Config config) {
        return create().config(config);
    }

    static TracerBuilder<ZipkinTracerBuilder> create() {
        return new ZipkinTracerBuilder();
    }

    @Override
    public ZipkinTracerBuilder serviceName(String name) {
        this.serviceName = name;
        return this;
    }

    @Override
    public ZipkinTracerBuilder collectorUri(URI uri) {
        TracerBuilder.super.collectorUri(uri);

        if (null != uri.getUserInfo()) {
            this.userInfo = uri.getUserInfo();
        }

        return this;
    }

    @Override
    public ZipkinTracerBuilder collectorProtocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    @Override
    public ZipkinTracerBuilder collectorHost(String host) {
        this.host = host;
        return this;
    }

    @Override
    public ZipkinTracerBuilder collectorPort(int port) {
        this.port = port;
        return this;
    }

    /**
     * Override path to use.
     * Path is usually defined by the protocol {@link Version}.
     * Use this to override if not using default (e.g. when
     * Zipkin is behind a reverse proxy that prefixes the path).
     *
     * @param path path to override the default defined by {@link Version}
     * @return updated builder instance
     */
    @Override
    public ZipkinTracerBuilder collectorPath(String path) {
        this.path = path;
        return this;
    }

    @Override
    public ZipkinTracerBuilder enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public ZipkinTracerBuilder addTracerTag(String key, String value) {
        this.tags.add(Tag.create(key, value));
        return this;
    }

    @Override
    public ZipkinTracerBuilder addTracerTag(String key, Number value) {
        this.tags.add(Tag.create(key, value));
        return this;
    }

    @Override
    public ZipkinTracerBuilder addTracerTag(String key, boolean value) {
        this.tags.add(Tag.create(key, value));
        return this;
    }

    @Override
    public ZipkinTracerBuilder registerGlobal(boolean global) {
        this.global = global;
        return this;
    }

    @Override
    public ZipkinTracerBuilder config(Config config) {
        config.get("enabled").asBoolean().ifPresent(this::enabled);
        config.get("service").asString().ifPresent(this::serviceName);
        config.get("protocol").asString().ifPresent(this::collectorProtocol);
        config.get("host").asString().ifPresent(this::collectorHost);
        config.get("port").asInt().ifPresent(this::collectorPort);
        config.get("path").asString().ifPresent(this::collectorPath);
        config.get("api-version").asString().ifPresent(this::configApiVersion);

        config.get("tags").detach()
                .asMap()
                .orElseGet(Map::of)
                .forEach(this::addTracerTag);

        config.get("boolean-tags")
                .asNodeList()
                .ifPresent(nodes -> {
                    nodes.forEach(node -> {
                        this.addTracerTag(node.key().name(), node.asBoolean().get());
                    });
                });

        config.get("int-tags")
                .asNodeList()
                .ifPresent(nodes -> {
                    nodes.forEach(node -> {
                        this.addTracerTag(node.key().name(), node.asInt().get());
                    });
                });

        config.get("global").asBoolean().ifPresent(this::registerGlobal);

        return this;
    }

    /**
     * Builds the {@link Tracer} for Zipkin based on the configured parameters.
     *
     * @return the tracer
     */
    @Override
    public Tracer build() {
        Tracer result;

        if (enabled) {
            if (null == serviceName) {
                throw new IllegalArgumentException(
                        "Configuration must at least contain the 'service' key ('tracing.service` in MP) with service name");
            }

            Sender buildSender = (this.sender == null) ? createSender() : this.sender;

            Reporter<Span> reporter = AsyncReporter.builder(buildSender)
                    .build(version.encoder());

            // Now, create a Brave tracing component with the service name you want to see in Zipkin.
            //   (the dependency is io.zipkin.brave:brave)
            Tracing braveTracing = Tracing.newBuilder()
                    .localServiceName(serviceName)
                    .spanReporter(reporter)
                    .build();

            if (null == sender) {
                LOGGER.info(() -> "Creating Zipkin Tracer for '" + serviceName + "' configured with: " + createEndpoint());
            } else {
                LOGGER.info(() -> "Creating Zipkin Tracer for '" + serviceName + "' with explicit sender: " + sender);
            }

            // use this to create an OpenTracing Tracer
            result = new ZipkinTracer(BraveTracer.create(braveTracing), new LinkedList<>(tags));
        } else {
            LOGGER.info("Zipkin Tracer is explicitly disabled.");
            result = NoopTracerFactory.create();
        }

        if (global) {
            GlobalTracer.registerIfAbsent(result);
        }

        return result;
    }

    /**
     * Version of Zipkin API to use.
     * Defaults to {@link Version#V2}.
     *
     * @param version version to use
     * @return updated builder instance
     */
    public ZipkinTracerBuilder version(Version version) {
        this.version = version;
        return this;
    }

    /**
     * The sender to use for sending events to Zipkin.
     * When configured, all {@link #collectorProtocol(String)},
     * {@link #collectorHost(String)}, {@link #collectorPort(int)}, and
     * {@link #version(Version)} methods are ignored.
     *
     * @param sender the sender to use
     * @return this builder
     */
    @SuppressWarnings("unused")
    public ZipkinTracerBuilder sender(Sender sender) {
        this.sender = sender;
        return this;
    }

    private void configApiVersion(String version) {
        String workingVersion = version.trim().toLowerCase();

        switch (workingVersion) {
        case "1":
        case "v1":
            version(Version.V1);
            return;
        case "2":
        case "v2":
            version(Version.V2);
            return;
        default:
            throw new IllegalArgumentException("Version in tracing configuration must be 1, 2, v1 or v2, but was: " + version);
        }
    }

    private URI createEndpoint() {
        String buildPath = ((null == path) ? version.path() : path);
        try {
            return new URI(
                    protocol,
                    userInfo,
                    host,
                    port,
                    buildPath,
                    null,
                    null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid Zipkin endpoint: " + protocol + "://" + host + ":" + port + buildPath, e);
        }
    }

    private Sender createSender() {
        URI endpoint = createEndpoint();

        try {
            return URLConnectionSender.newBuilder().endpoint(endpoint.toURL()).build();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Cannot convert to URL: " + endpoint, e);
        }
    }

    List<Tag<?>> tags() {
        return tags;
    }

    String serviceName() {
        return serviceName;
    }

    String protocol() {
        return protocol;
    }

    String host() {
        return host;
    }

    int port() {
        return port;
    }

    String path() {
        return path;
    }

    Version version() {
        return version;
    }

    Sender sender() {
        return sender;
    }

    String userInfo() {
        return userInfo;
    }

    boolean isEnabled() {
        return enabled;
    }

    /**
     * Versions available for Zipkin API.
     */
    public enum Version {
        /**
         * Version 1.
         */
        V1("/api/v1/spans", SpanBytesEncoder.JSON_V1),
        /**
         * Version 2.
         */
        V2("/api/v2/spans", SpanBytesEncoder.JSON_V2);

        private final String path;
        private final BytesEncoder<Span> encoder;

        Version(String path, SpanBytesEncoder encoder) {
            this.path = path;
            this.encoder = encoder;
        }

        String path() {
            return path;
        }

        BytesEncoder<Span> encoder() {
            return encoder;
        }
    }
}
