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

package io.helidon.security;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import io.helidon.security.util.AbacSupport;

/**
 * Security environment is a set of attributes that are stable for an interaction (usually a request in our case).
 * Environment can be re-used for multiple security request (e.g authentication, authorization).
 * Access to environment is either through methods (for known attributes) or through
 * generic {@link #abacAttribute(String)} methods for any property configured by integration component.
 *
 * <p>
 * The following properties are available (known):
 * <ul>
 * <li>time: decision time of the current request (e.g. when checking that this is within business hours</li>
 * <li>uri: target URI that was requested</li>
 * <li>path: path that was requested</li>
 * <li>method: method of the request</li>
 * <li>transport: transport of the request (e.g. http)</li>
 * <li>headers: transport headers of the request (map)</li>
 * </ul>
 */
public class SecurityEnvironment implements AbacSupport {
    private final AbacSupport properties;
    private final SecurityTime timeProvider;
    private final ZonedDateTime time;
    private final URI targetUri;
    private final String method;
    private final String transport;
    private final Optional<String> path;
    private final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private SecurityEnvironment(Builder builder) {
        BasicAttributes basicAttributes = BasicAttributes.create(builder.attributes);
        this.timeProvider = builder.timeProvider;
        this.time = builder.time;
        this.targetUri = builder.targetUri;
        this.path = Optional.ofNullable(builder.path);
        this.method = builder.method;
        this.transport = builder.transport;
        this.headers.putAll(builder.headers);

        basicAttributes.put("time", time);
        basicAttributes.put("uri", targetUri);
        basicAttributes.put("path", path);
        basicAttributes.put("method", method);
        basicAttributes.put("transport", transport);
        basicAttributes.put("headers", headers);
        this.properties = basicAttributes;
    }

    /**
     * Creates a fluent API builder to build new instances of this class.
     *
     * @param serverTime Time to use to obtain current time
     * @return a builder instance
     */
    public static Builder builder(SecurityTime serverTime) {
        return new Builder(serverTime);
    }

    /**
     * Creates a fluent API builder to build new instances of this class with current time.
     *
     * @return a builder instance
     */
    public static Builder builder() {
        return new Builder(SecurityTime.create());
    }

    /**
     * Create a new instance of security environment with all default values.
     *
     * @return environment instance
     */
    public static SecurityEnvironment create() {
        return builder().build();
    }

    @Override
    public Object abacAttributeRaw(String key) {
        return properties.abacAttributeRaw(key);
    }

    @Override
    public Collection<String> abacAttributeNames() {
        return properties.abacAttributeNames();
    }

    /**
     * Time on the server this environment was created for current request.
     * This should be treated as the "decisive" time of the request for security evaluation.
     *
     * This can be configured - e.g. there can be a time-shift (moving time by a specific amount of seconds to the past or
     * to the future), or an explicit value (e.g. setting the time to 14:00 e.g. for testing purposes).
     *
     * @return server time that should be used to make security decisions
     * @see io.helidon.security.Security.Builder#serverTime()
     */
    public ZonedDateTime time() {
        return time;
    }

    /**
     * Get the URI of the resource requested. For inbound request, this contains the requested URI by
     * remote client (or as close to the original one as we can get), for outbound requests, this contains
     * the actual URI as configured by client to be called on remote server.
     * TODO if we use service registry, we must have access to the actual endpoint (as signatures may require signing of
     * URI with the real host and port). Either this method MUST return a resolved URI, or we MUST have access to registry
     * and enforce an endpoint (when resolved).
     *
     * @return URI being called or URI to be called
     */
    public URI targetUri() {
        return targetUri;
    }

    /**
     * Path to the resource.
     * For jax-rs, this is relative URI.
     *
     * @return Path to the resource
     */
    public Optional<String> path() {
        return path;
    }

    /**
     * Verb to execute on the resource.
     * For http, this is HTTP method (PUT, GET, DELETE, POST....)
     *
     * @return Verb executing on the resource, default is GET
     */
    public String method() {
        return method;
    }

    /**
     * Return type of transport (such as http, https, jms etc.).
     * Transport should be case insensitive, yet I recommend using all lower case.
     * For the purpose of this method, http and https are two separate transports!
     *
     * @return transport used for this request. Defaults to http.
     */
    public String transport() {
        return transport;
    }

    /**
     * Derive a new environment builder based on this environment.
     *
     * @return builder to build a new environment overriding only needed values with a new timestamp
     */
    public Builder derive() {
        return builder(timeProvider)
                .attributes(properties)
                .targetUri(targetUri)
                .method(method)
                .transport(transport)
                .path(path.orElse(null))
                .headers(headers);
    }

    /**
     * Transport headers that can be used to process the message.
     * The headers stand here as a generalization - they cover all metadata sent with each request that is not
     * described elsewhere.
     * For HTTP, this would cover: all HTTP headers (done automatically by integration components), on-demand
     * query parameters (must be explicitly configured and supported by integration component), on-demand
     * form parameters (must be explicitly configured and supported by integration component).
     * For JMS, this would cover: all JMS headers (in string form - byte[] should be base64 encoded).
     * Other protocols must choose a reasonable way to transfer a request/response message into headers and entity.
     *
     * @return Header map. If transport protocol does not support headers, map will be empty
     */
    public Map<String, List<String>> headers() {
        return headers;
    }

    /**
     * A fluent API builder for {@link SecurityEnvironment}.
     */
    public static final class Builder implements io.helidon.common.Builder<SecurityEnvironment> {
        /**
         * Default transport is {@value}.
         */
        public static final String DEFAULT_TRANSPORT = "http";
        /**
         * Default method is {@value}.
         */
        public static final String DEFAULT_METHOD = "GET";

        private final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private SecurityTime timeProvider;
        private ZonedDateTime time;
        private BasicAttributes attributes = BasicAttributes.create();
        private URI targetUri;
        private String path;
        private String method = DEFAULT_METHOD;
        private String transport = DEFAULT_TRANSPORT;

        private Builder(SecurityTime timeProvider) {
            this.timeProvider = timeProvider;
        }

        @Override
        public SecurityEnvironment build() {
            time = timeProvider.get();

            return new SecurityEnvironment(this);
        }

        private Builder attributes(AbacSupport props) {
            this.attributes = BasicAttributes.create(props);
            return this;
        }

        /**
         * Add an attribute to this environment.
         *
         * @param key   name of the attribute
         * @param value value of the attribute
         * @return updated builder instance
         */
        public Builder addAttribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        /**
         * Transport headers (such as HTTP headers, JMS headers).
         * Headers are case insensitive.
         *
         * @param headers header map
         * @return this instance
         */
        public Builder headers(Map<String, List<String>> headers) {
            this.headers.putAll(headers);
            return this;
        }

        /**
         * We may want to clear existing headers, such as when deriving an environment
         * for outbound calls.
         *
         * @return this instance
         */
        public Builder clearHeaders() {
            this.headers.clear();
            return this;
        }

        /**
         * Add a single-value header. Note that if method {@link #headers(Map)} is called after
         * this method, it will remove changes by this method.
         *
         * @param header header name
         * @param value  header value
         * @return this instance
         */
        public Builder header(String header, String value) {
            this.headers.put(header, List.of(value));
            return this;
        }

        /**
         * Add a multi-value header. Note that if method {@link #headers(Map)} is called after
         * this method, it may remove changes by this method.
         *
         * @param header header name
         * @param values header values
         * @return this instance
         */
        public Builder header(String header, List<String> values) {
            this.headers.put(header, values);
            return this;
        }

        /**
         * Configure target URI.
         *
         * @param uri target URI being (or to be) called. If this is an unusual protocol, build the uri following similar pattern
         *            to HTTP (jms://host:port/connFactory/queueJndi; socket://host:port; teleport://newyork/broadway/44
         * @return this instance
         */
        public Builder targetUri(URI uri) {
            this.targetUri = uri;
            return this;
        }

        /**
         * Path that is requested (such as URI for http, without protocol, server and port).
         *
         * @param path the path
         * @return this instance
         */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /**
         * Method that is requested (such as GET/POST for http).
         * Default is {@value #DEFAULT_METHOD}.
         *
         * @param method the method
         * @return this instance
         */
        public Builder method(String method) {
            this.method = method;
            return this;
        }

        /**
         * Transport we are implementing (such as http, https).
         * Default is {@link #DEFAULT_TRANSPORT}.
         *
         * @param transport the transport
         * @return this instance
         */
        public Builder transport(String transport) {
            this.transport = transport;
            return this;
        }

        /**
         * Use the defined time to obtain current time.
         *
         * @param time SecurityTime that allows for explicit values being set (e.g. for unit tests)
         * @return updated builder instance
         */
        public Builder time(SecurityTime time) {
            this.timeProvider = time;
            return this;
        }
    }
}
