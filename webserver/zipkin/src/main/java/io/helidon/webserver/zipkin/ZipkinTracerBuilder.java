/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.zipkin;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.common.Builder;
import io.helidon.config.Config;

import brave.Tracing;
import brave.opentracing.BraveTracer;
import io.opentracing.Tracer;
import zipkin2.Span;
import zipkin2.codec.BytesEncoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.Sender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * The ZipkinTracerBuilder is a convenience builder for  {@link Tracer} to use with Zipkin.
 *
 * @see <a href="http://zipkin.io/pages/instrumenting.html#core-data-structures">Zipkin Attributes</a>
 * @see <a href="https://github.com/openzipkin/zipkin/issues/962">Zipkin Missing Service Name</a>
 */
public final class ZipkinTracerBuilder implements Builder<Tracer> {

    private static final Logger LOGGER = Logger.getLogger(ZipkinTracerBuilder.class.getName());
    private static final String DEFAULT_PROTOCOL = "http";
    private static final int DEFAULT_ZIPKIN_PORT = 9411;
    private static final String DEFAULT_ZIPKIN_HOST = "127.0.0.1";
    private static final Version DEFAULT_VERSION = Version.V2;

    private String serviceName;
    private String protocol = DEFAULT_PROTOCOL;
    private String host = DEFAULT_ZIPKIN_HOST;
    private int port = DEFAULT_ZIPKIN_PORT;
    private String path;
    private Version version = DEFAULT_VERSION;
    private Sender sender;
    private String userInfo;

    private ZipkinTracerBuilder(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * Get a Zipkin {@link Tracer} builder for processing tracing data of a service with a given name.
     *
     * @param serviceName name of the service that will be using the tracer.
     * @return {@code Tracer} builder for Zipkin.
     */
    public static ZipkinTracerBuilder forService(String serviceName) {
        return new ZipkinTracerBuilder(serviceName);
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
     * Override path to use.
     * Path is usually defined by the protocol {@link Version}.
     * Use this to override if not using default (e.g. when
     * Zipkin is behind a reverse proxy that prefixes the path).
     *
     * @param path path to override the default defined by {@link Version}
     * @return updated builder instance
     */
    public ZipkinTracerBuilder path(String path) {
        this.path = path;
        return this;
    }

    /**
     * Create a new builder based on values in configuration.
     * This requires at least a key "service" in the provided config.
     *
     * @param config configuration to load this builder from
     * @return a new builder instance.
     * @see ZipkinTracerBuilder#fromConfig(Config)
     */
    public static ZipkinTracerBuilder from(Config config) {
        String serviceName = config.get("service").value()
                .orElseThrow(() -> new IllegalArgumentException("Configuration must at least contain the service key"));

        return ZipkinTracerBuilder.forService(serviceName)
                .fromConfig(config);
    }

    /**
     * Update this builder from configuration.
     *
     * @param config configuration that contains at least the key service (if using configuration only).
     * @return updated builder instance
     */
    public ZipkinTracerBuilder fromConfig(Config config) {
        config.get("service").value().ifPresent(this::serviceName);
        config.get("protocol").value().ifPresent(this::protocol);
        config.get("host").value().ifPresent(this::zipkinHost);
        config.get("port").asOptionalInt().ifPresent(this::port);
        config.get("path").value().ifPresent(this::path);
        config.get("api-version").value().ifPresent(this::configApiVersion);

        return this;
    }

    /**
     * Set the Zipkin endpoint address.
     * <p>
     * The resulting {@link Tracer} will be sending any tracing data to this location. The significant parts of the URI
     * are {@code userInfo}, {@code host} and {@code port}. Other parts of the endpoint URI are ignored.
     *
     * @param endpoint Zipkin endpoint address. Must not be {@code null}.
     * @return updated builder.
     */
    public ZipkinTracerBuilder zipkin(URI endpoint) {
        Optional.ofNullable(endpoint.getScheme()).ifPresent(this::protocol);
        Optional.ofNullable(endpoint.getHost()).ifPresent(this::zipkinHost);
        if (endpoint.getPort() != -1) {
            port(endpoint.getPort());
        }
        if (null != endpoint.getUserInfo()) {
            this.userInfo = endpoint.getUserInfo();
        }

        return this;
    }

    /**
     * Set the Zipkin endpoint address.
     * <p>
     * The resulting {@link Tracer} will be sending any tracing data to this location. The significant parts of the endpoint URI
     * are {@code userInfo}, {@code host} and {@code port}. Other parts of the endpoint URI are ignored.
     *
     * @param endpoint Zipkin endpoint address. May be {@code null}, in which case the default Zipkin endpoint address
     *                 will be used ({@code http://127.0.0.1:9411/}).
     * @return updated builder.
     */
    public ZipkinTracerBuilder zipkin(String endpoint) {
        if (endpoint != null) {
            zipkin(URI.create(endpoint));
        }
        return this;
    }

    /**
     * Set the Zipkin host name.
     * <p>
     * The resulting {@link Tracer} will be sending any tracing data to the Zipkin location address constructed using the
     * supplied host name and the default Zipkin port (i.e. {@code http://<hostName>:9411/}).
     *
     * @param hostName Zipkin host name.
     * @return updated builder.
     */
    public ZipkinTracerBuilder zipkinHost(String hostName) {
        this.host = hostName;
        return this;
    }

    /**
     * Set the protocol to use (http, https). Defaults to {@value #DEFAULT_PROTOCOL}.
     *
     * @param protocol protocol to use
     * @return updated builder instance
     */
    public ZipkinTracerBuilder protocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    /**
     * Set the port to use. Defaults to {@value #DEFAULT_ZIPKIN_PORT}.
     *
     * @param port port of the zipkin server
     * @return updated builder instance
     */
    public ZipkinTracerBuilder port(int port) {
        this.port = port;
        return this;
    }

    /**
     * The sender to use for sending events to Zipkin.
     * When configured, all {@link #protocol(String)}, {@link #zipkinHost(String)}, {@link #port(int)}, and
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

    /**
     * Builds the {@link Tracer} for Zipkin based on the configured parameters.
     *
     * @return the tracer
     */
    public Tracer build() {
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
        return new ZipkinTracer(BraveTracer.create(braveTracing));
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

    private void serviceName(String serviceName) {
        this.serviceName = serviceName;
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
