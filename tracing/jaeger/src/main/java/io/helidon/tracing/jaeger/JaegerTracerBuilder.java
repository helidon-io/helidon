/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.tracing.jaeger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.tracing.TracerBuilder;

import io.jaegertracing.Configuration;
import io.jaegertracing.internal.JaegerTracer;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.util.GlobalTracer;

/**
 * The JaegerTracerBuilder is a convenience builder for {@link io.opentracing.Tracer} to use with Jaeger.
 * <p>
 * <b>Unless You want to explicitly depend on Jaeger in Your code, please
 * use {@link io.helidon.tracing.TracerBuilder#create(String)} or
 * {@link io.helidon.tracing.TracerBuilder#create(io.helidon.config.Config)} that is abstracted.</b>
 * <p>
 * The Jaeger tracer uses environment variables and system properties to override the defaults.
 * Except for {@code protocol} and {@code service} these are honored, unless overridden in configuration
 * or through the builder methods.
 * See <a href="https://github.com/jaegertracing/jaeger-client-java/blob/master/jaeger-core/README.md">Jaeger documentation</a>
 *  for details.
 * <p>
 * The following table lists jaeger specific defaults and configuration options.
 * <table class="config">
 *     <caption>Tracer Configuration Options</caption>
 *     <tr>
 *         <th>option</th>
 *         <th>default</th>
 *         <th>description</th>
 *     </tr>
 *     <tr>
 *         <td>{@code service}</td>
 *         <td>&nbsp;</td>
 *         <td>Required service name</td>
 *     </tr>
 *     <tr>
 *         <td>{@code protocol}</td>
 *         <td>{@code http}</td>
 *         <td>The protocol to use. By default http is used. To switch to agent
 *          mode, use {@code udp}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code host}</td>
 *         <td>{@code 127.0.0.1} for {@code http}, library default for {@code udp}</td>
 *         <td>Host to used - used by both UDP and HTTP endpoints</td>
 *     </tr>
 *     <tr>
 *         <td>{@code port}</td>
 *         <td>{@code 14268} for {@code http}, library default for {@code udp}</td>
 *         <td>Port to be used - used by both UDP and HTTP endpoints</td>
 *     </tr>
 *     <tr>
 *         <td>{@code path}</td>
 *         <td>{@code /api/traces}</td>
 *         <td>Path to be used when using {@code http}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code token}</td>
 *         <td>&nbsp;</td>
 *         <td>Authentication token to use</td>
 *     </tr>
 *     <tr>
 *         <td>{@code username}</td>
 *         <td>&nbsp;</td>
 *         <td>User to use to authenticate (basic authentication)</td>
 *     </tr>
 *     <tr>
 *         <td>{@code password}</td>
 *         <td>&nbsp;</td>
 *         <td>Password to use to authenticate (basic authentication)</td>
 *     </tr>
 *     <tr>
 *         <td>{@code propagation}</td>
 *         <td>library default</td>
 *         <td>Propagation type to use, supports {@code jaeger} and {@code b3}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code log-spans}</td>
 *         <td>library default</td>
 *         <td>Whether reporter should log spans</td>
 *     </tr>
 *     <tr>
 *         <td>{@code max-queue-size}</td>
 *         <td>library default</td>
 *         <td>Maximal queue size of the reporter</td>
 *     </tr>
 *     <tr>
 *         <td>{@code flush-interval-ms}</td>
 *         <td>library default</td>
 *         <td>Reporter flush interval in milliseconds</td>
 *     </tr>
 *     <tr>
 *         <td>{@code sampler-type}</td>
 *         <td>library default</td>
 *         <td>Sampler type ({@code probabilistic}, {@code ratelimiting}, or {@code remote}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code sampler-param}</td>
 *         <td>library default</td>
 *         <td>Numeric parameter specifying details for the sampler type (see Jaeger docs)</td>
 *     </tr>
 *     <tr>
 *         <td>sampler-manager</td>
 *         <td>library default</td>
 *         <td>host and port of the sampler manager</td>
 *     </tr>
 *     <tr>
 *         <td>{@code tags}</td>
 *         <td>&nbsp;</td>
 *         <td>see {@link io.helidon.tracing.TracerBuilder}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code boolean-tags}</td>
 *         <td>&nbsp;</td>
 *         <td>see {@link io.helidon.tracing.TracerBuilder}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code int-tags}</td>
 *         <td>&nbsp;</td>
 *         <td>see {@link io.helidon.tracing.TracerBuilder}</td>
 *     </tr>
 * </table>
 *
 * @see <a href="https://github.com/jaegertracing/jaeger-client-java/blob/master/jaeger-core/README.md">Jaeger configuration</a>
 */
public class JaegerTracerBuilder implements TracerBuilder<JaegerTracerBuilder> {
    static final Logger LOGGER = Logger.getLogger(JaegerTracerBuilder.class.getName());

    static final boolean DEFAULT_ENABLED = true;
    static final String DEFAULT_HTTP_HOST = "localhost";
    static final int DEFAULT_HTTP_PORT = 14268;
    static final String DEFAULT_HTTP_PATH = "/api/traces";


    private final Map<String, String> tags = new HashMap<>();
    private final List<Configuration.Propagation> propagations = new ArrayList<>();
    private String serviceName;
    private String protocol;
    private String host;
    private Integer port;
    private String path;
    private String token;
    private String username;
    private String password;
    private Boolean reporterLogSpans;
    private Integer reporterMaxQueueSize;
    private Long reporterFlushIntervalMillis;
    private SamplerType samplerType;
    private Number samplerParam;
    private String samplerManager;
    private boolean enabled = DEFAULT_ENABLED;
    private boolean global = true;

    /**
     * Default constructor, does not modify any state.
     */
    protected JaegerTracerBuilder() {
    }

    /**
     * Get a Jaeger {@link io.opentracing.Tracer} builder for processing tracing data of a service with a given name.
     *
     * @param serviceName name of the service that will be using the tracer.
     * @return {@code Tracer} builder for Jaeger.
     */
    public static JaegerTracerBuilder forService(String serviceName) {
        return create()
                .serviceName(serviceName);
    }

    /**
     * Create a new builder based on values in configuration.
     * This requires at least a key "service" in the provided config.
     *
     * @param config configuration to load this builder from
     * @return a new builder instance.
     * @see io.helidon.tracing.jaeger.JaegerTracerBuilder#config(io.helidon.config.Config)
     */
    public static JaegerTracerBuilder create(Config config) {
        return create().config(config);
    }

    static TracerBuilder<JaegerTracerBuilder> create() {
        return new JaegerTracerBuilder();
    }

    @Override
    public JaegerTracerBuilder serviceName(String name) {
        this.serviceName = name;
        return this;
    }

    @Override
    public JaegerTracerBuilder collectorProtocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    @Override
    public JaegerTracerBuilder collectorHost(String host) {
        this.host = host;
        return this;
    }

    @Override
    public JaegerTracerBuilder collectorPort(int port) {
        this.port = port;
        return this;
    }

    /**
     * Override path to use.
     *
     * @param path path to override the default
     * @return updated builder instance
     */
    @Override
    public JaegerTracerBuilder collectorPath(String path) {
        this.path = path;
        return this;
    }

    @Override
    public JaegerTracerBuilder enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public JaegerTracerBuilder addTracerTag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }

    @Override
    public JaegerTracerBuilder addTracerTag(String key, Number value) {
        this.tags.put(key, String.valueOf(value));
        return this;
    }

    @Override
    public JaegerTracerBuilder addTracerTag(String key, boolean value) {
        this.tags.put(key, String.valueOf(value));
        return this;
    }

    @Override
    public JaegerTracerBuilder registerGlobal(boolean global) {
        this.global = global;
        return this;
    }

    /**
     * Configure username and password for basic authentication.
     *
     * @param username username to use
     * @param password password to use
     * @return updated builder instance
     */
    public JaegerTracerBuilder basicAuth(String username, String password) {
        username(username);
        password(password);
        return this;
    }

    /**
     * Add propagation type to use.
     *
     * @param propagation propagation value
     * @return updated builder instance
     */
    public JaegerTracerBuilder addPropagation(Configuration.Propagation propagation) {
        this.propagations.add(propagation);

        return this;
    }

    @Override
    public JaegerTracerBuilder config(Config config) {
        config.get("enabled").asBoolean().ifPresent(this::enabled);
        config.get("service").asString().ifPresent(this::serviceName);
        config.get("protocol").asString().ifPresent(this::collectorProtocol);
        config.get("host").asString().ifPresent(this::collectorHost);
        config.get("port").asInt().ifPresent(this::collectorPort);
        config.get("path").asString().ifPresent(this::collectorPath);
        config.get("token").asString().ifPresent(this::token);
        config.get("username").asString().ifPresent(this::username);
        config.get("password").asString().ifPresent(this::password);
        config.get("propagation").asList(String.class).ifPresent(propagations -> {
            propagations.stream()
                    .map(String::toUpperCase)
                    .map(Configuration.Propagation::valueOf)
                    .forEach(this::addPropagation);

        });
        config.get("log-spans").asBoolean().ifPresent(this::logSpans);
        config.get("max-queue-size").asInt().ifPresent(this::maxQueueSize);
        config.get("flush-interval-ms").asLong().ifPresent(this::flushIntervalMs);
        config.get("sampler-type").asString().as(SamplerType::create).ifPresent(this::samplerType);
        config.get("sampler-param").asDouble().ifPresent(this::samplerParam);
        config.get("sampler-manager").asString().ifPresent(this::samplerManager);

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
     * The host name and port when using the remote controlled sampler.
     *
     * @param samplerManagerHostPort host and port of the sampler manager
     * @return updated builder instance
     */
    public JaegerTracerBuilder samplerManager(String samplerManagerHostPort) {
        this.samplerManager = samplerManagerHostPort;
        return this;
    }

    /**
     * The sampler parameter (number).
     * @param samplerParam parameter of the sampler
     * @return updated builder instance
     */
    public JaegerTracerBuilder samplerParam(Number samplerParam) {
        this.samplerParam = samplerParam;
        return this;
    }

    /**
     * Sampler type.
     * <p>
     * See <a href="https://www.jaegertracing.io/docs/latest/sampling/#client-sampling-configuration">Sampler types</a>.
     *
     * @param samplerType type of the sampler
     * @return updated builder instance
     */
    public JaegerTracerBuilder samplerType(SamplerType samplerType) {
        this.samplerType = samplerType;
        return this;
    }

    /**
     * The reporter's flush interval.
     *
     * @param value amount in the unit specified
     * @param timeUnit the time unit
     * @return updated builder instance
     */
    public JaegerTracerBuilder flushInterval(long value, TimeUnit timeUnit) {
        flushIntervalMs(timeUnit.toMillis(value));
        return this;
    }

    /**
     * The reporter's maximum queue size.
     *
     * @param maxQueueSize maximal size of the queue
     * @return updated builder instance
     */
    public JaegerTracerBuilder maxQueueSize(int maxQueueSize) {
        this.reporterMaxQueueSize = maxQueueSize;
        return this;
    }

    /**
     * Whether the reporter should also log the spans.
     *
     * @param logSpans whether to log spans
     * @return updated builder instance
     */
    public JaegerTracerBuilder logSpans(boolean logSpans) {
        this.reporterLogSpans = logSpans;
        return this;
    }

    /**
     * Authentication token sent as a "Bearer" to the endpoint.
     *
     * @param token token to authenticate
     * @return updated builder instance
     */
    public JaegerTracerBuilder token(String token) {
        this.token = token;
        return this;
    }

    /**
     * Builds the {@link io.opentracing.Tracer} for Jaeger based on the configured parameters.
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

            JaegerTracer.Builder builder = jaegerConfig().getTracerBuilder();
            builder.withScopeManager(new JaegerScopeManager());     // use our scope manager
            result = builder.build();
            LOGGER.info(() -> "Creating Jaeger tracer for '" + serviceName + "' configured with " + protocol + "://"
                    + host + ":" + port);
        } else {
            LOGGER.info("Jaeger Tracer is explicitly disabled.");
            result = NoopTracerFactory.create();
        }

        if (global) {
            GlobalTracer.registerIfAbsent(result);
        }

        return result;
    }

    Configuration jaegerConfig() {
        /*
         * Preload from environment, then override configured values
         */
        Configuration config = Configuration.fromEnv(serviceName);

        if (null != tags) {
            config.withTracerTags(tags);
        }

        /*
         * Sender configuration
         */
        Configuration.SenderConfiguration sender = Configuration.SenderConfiguration.fromEnv();
        if ((null == protocol) || "http".equals(protocol) || "https".equals(protocol)) {
            if (null == host) {
                host = DEFAULT_HTTP_HOST;
            }
            if (null == port) {
                port = DEFAULT_HTTP_PORT;
            }
            if (null == path) {
                path = DEFAULT_HTTP_PATH;
            }
            if (null == protocol) {
                protocol = "http";
            }
            sender.withEndpoint(protocol + "://" + host + ":" + port + path);
        } else if ("udp".equals(protocol)) {
            // UDP
            if (null != host) {
                sender.withAgentHost(host);
            }
            if (null != port) {
                sender.withAgentPort(port);
            }
        } // else use library defaults for other type of protocols

        if (null != token) {
            sender.withAuthToken(token);
        } else {
            // token has precedence over basic auth
            if (null != username) {
                sender.withAuthUsername(username);
            }
            if (null != password) {
                sender.withAuthPassword(password);
            }
        }

        Configuration.ReporterConfiguration reporter = Configuration.ReporterConfiguration.fromEnv();
        if (null != reporterLogSpans) {
            reporter.withLogSpans(reporterLogSpans);
        }
        if (null != reporterMaxQueueSize) {
            reporter.withMaxQueueSize(reporterMaxQueueSize);
        }
        if (null != reporterFlushIntervalMillis) {
            reporter.withFlushInterval(reporterFlushIntervalMillis.intValue());
        }

        reporter.withSender(sender);
        config.withReporter(reporter);

        if (null != samplerType) {
            Configuration.SamplerConfiguration sampler = Configuration.SamplerConfiguration.fromEnv();
            sampler.withType(samplerType.config);

            if (null != samplerParam) {
                sampler.withParam(samplerParam);
            }

            if (null != samplerManager) {
                sampler.withManagerHostPort(samplerManager);
            }

            config.withSampler(sampler);
        }

        if (!propagations.isEmpty()) {
            Configuration.CodecConfiguration codec = Configuration.CodecConfiguration.fromEnv();
            for (Configuration.Propagation propagation : propagations) {
                codec.withPropagation(propagation);
            }
            config.withCodec(codec);
        }

        return config;
    }

    Map<String, String> tags() {
        return tags;
    }

    List<Configuration.Propagation> propagations() {
        return propagations;
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

    Integer port() {
        return port;
    }

    String path() {
        return path;
    }

    String token() {
        return token;
    }

    String username() {
        return username;
    }

    String password() {
        return password;
    }

    Boolean reporterLogSpans() {
        return reporterLogSpans;
    }

    Integer reporterMaxQueueSize() {
        return reporterMaxQueueSize;
    }

    Long reporterFlushIntervalMillis() {
        return reporterFlushIntervalMillis;
    }

    SamplerType samplerType() {
        return samplerType;
    }

    Number samplerParam() {
        return samplerParam;
    }

    String samplerManager() {
        return samplerManager;
    }

    boolean isEnabled() {
        return enabled;
    }

    private void username(String username) {
        this.username = username;
    }

    private void password(String password) {
        this.password = password;
    }

    private void flushIntervalMs(Long aLong) {
        this.reporterFlushIntervalMillis = aLong;
    }

    enum SamplerType {
        CONSTANT("const"),
        PROBABILISTIC("probabilistic"),
        RATE_LIMITING("ratelimiting"),
        REMOTE("remote");
        private final String config;

        SamplerType(String config) {
            this.config = config;
        }

        String config() {
            return config;
        }

        static SamplerType create(String value) {
            for (SamplerType sampler : SamplerType.values()) {
                if (sampler.config().equals(value)) {
                    return sampler;
                }
            }
            throw new IllegalStateException("SamplerType " + value + " is not supported");
        }
    }
}
