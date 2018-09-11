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
import java.util.logging.Logger;

import io.helidon.common.Builder;

import brave.Tracing;
import brave.opentracing.BraveTracer;
import io.opentracing.Tracer;
import zipkin2.Span;
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
public class ZipkinTracerBuilder implements Builder<Tracer> {

    private static final Logger LOGGER = Logger.getLogger(ZipkinTracerBuilder.class.getName());
    private static final int DEFAULT_ZIPKIN_PORT = 9411;
    private static final String DEFAULT_ZIPKIN_HOST = "127.0.0.1";
    private static final String DEFAULT_ZIPKIN_API_PATH = "/api/v1/spans";

    private final String serviceName;
    private URI zipkinEndpoint;

    private Sender sender;

    private ZipkinTracerBuilder(String serviceName) {
        this.serviceName = serviceName;
        this.zipkinEndpoint = defaultEndpoint(DEFAULT_ZIPKIN_HOST);
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

    private static URI defaultEndpoint(String host) {
        try {
            return new URI(
                    "http",
                    null,
                    host != null ? host : DEFAULT_ZIPKIN_HOST,
                    DEFAULT_ZIPKIN_PORT,
                    DEFAULT_ZIPKIN_API_PATH,
                    null,
                    null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid Zipkin endpoint.", e);
        }
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
        try {
            this.zipkinEndpoint = new URI(
                    "http",
                    endpoint.getUserInfo(),
                    endpoint.getHost() != null ? endpoint.getHost() : DEFAULT_ZIPKIN_HOST,
                    endpoint.getPort() >= 0 ? endpoint.getPort() : DEFAULT_ZIPKIN_PORT,
                    DEFAULT_ZIPKIN_API_PATH,
                    null,
                    null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid Zipkin endpoint.", e);
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
        } else {
            this.zipkinEndpoint = defaultEndpoint(null);
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
        this.zipkinEndpoint = defaultEndpoint(hostName);
        return this;
    }

    /**
     * The sender to use for sending events to Zipkin.
     *
     * @param sender the sender to use
     * @return this builder
     */
    @SuppressWarnings("unused")
    public ZipkinTracerBuilder sender(Sender sender) {
        this.sender = sender;
        return this;
    }

    private Sender defaultSender() {
        try {
            return URLConnectionSender.newBuilder().endpoint(zipkinEndpoint.toURL()).build();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Cannot convert to URL: " + zipkinEndpoint, e);
        }
    }

    /**
     * Builds the {@link Tracer} for Zipkin based on the configured parameters.
     *
     * @return the tracer
     */
    public Tracer build() {
        Sender sender = this.sender != null ? this.sender : defaultSender();

        Reporter<Span> reporter = AsyncReporter.builder(sender).build();

        // Now, create a Brave tracing component with the service name you want to see in Zipkin.
        //   (the dependency is io.zipkin.brave:brave)
        Tracing braveTracing = Tracing.newBuilder()
                .localServiceName(serviceName)
                .spanReporter(reporter)
                .build();

        LOGGER.info(() -> "Creating Zipkin Tracer for '" + serviceName + "' configured with: " + zipkinEndpoint);

        // use this to create an OpenTracing Tracer
        return new ZipkinTracer(BraveTracer.create(braveTracing));
    }
}
