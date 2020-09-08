/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.webclient;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import io.helidon.common.LazyValue;
import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.config.DeprecatedConfig;
import io.helidon.media.common.MediaContext;
import io.helidon.media.common.MediaContextBuilder;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.common.MessageBodyStreamReader;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.common.MessageBodyWriterContext;
import io.helidon.media.common.ParentingMediaContextBuilder;
import io.helidon.webclient.spi.WebClientService;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

/**
 * Configuration of the Helidon web client.
 */
class WebClientConfiguration {

    private final WebClientRequestHeaders clientHeaders;
    private final WebClientCookieManager cookieManager;
    private final CookiePolicy cookiePolicy;
    private final Config config;
    private final Context context;
    private final Duration connectTimeout;
    private final boolean enableAutomaticCookieStore;
    private final Duration readTimeout;
    private final LazyValue<String> userAgent;
    private final List<WebClientService> clientServices;
    private final Proxy proxy;
    private final boolean followRedirects;
    private final boolean keepAlive;
    private final int maxRedirects;
    private final MessageBodyReaderContext readerContext;
    private final MessageBodyWriterContext writerContext;
    private final WebClientTls webClientTls;
    private final URI uri;
    private final boolean validateHeaders;

    /**
     * Creates a new instance of client configuration.
     *
     * @param builder configuration builder
     */
    WebClientConfiguration(Builder<?, ?> builder) {
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.followRedirects = builder.followRedirects;
        this.userAgent = builder.userAgent;
        this.proxy = builder.proxy;
        this.webClientTls = builder.webClientTls;
        this.maxRedirects = builder.maxRedirects;
        this.clientHeaders = builder.clientHeaders;
        this.cookiePolicy = builder.cookiePolicy;
        this.enableAutomaticCookieStore = builder.enableAutomaticCookieStore;
        this.cookieManager = WebClientCookieManager.create(cookiePolicy,
                                                           builder.cookieStore,
                                                           builder.defaultCookies,
                                                           enableAutomaticCookieStore);
        this.config = builder.config;
        this.context = builder.context;
        this.readerContext = builder.readerContext;
        this.writerContext = builder.writerContext;
        this.clientServices = Collections.unmodifiableList(builder.clientServices);
        this.uri = builder.uri;
        this.keepAlive = builder.keepAlive;
        this.validateHeaders = builder.validateHeaders;
    }

    /**
     * Creates new builder to build a new instance of this class.
     *
     * @return a new builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Derives a new builder based on current instance of this class.
     *
     * @return a new builder instance
     */
    Builder derive() {
        return new Builder().update(this);
    }

    Optional<SslContext> sslContext() {
        SslContext sslContext;
        try {
            if (webClientTls.sslContext().isPresent()) {
                sslContext = nettySslFromJavaNet(webClientTls.sslContext().get());
            } else {
                SslContextBuilder sslContextBuilder = SslContextBuilder
                        .forClient()
                        .sslProvider(SslProvider.JDK);
                if (webClientTls.certificates().size() > 0) {
                    sslContextBuilder.trustManager(webClientTls.certificates().toArray(new X509Certificate[0]));
                }
                if (webClientTls.clientPrivateKey().isPresent()) {
                    sslContextBuilder.keyManager(webClientTls.clientPrivateKey().get(),
                                                 webClientTls.clientCertificateChain().toArray(new X509Certificate[0]));
                }

                if (webClientTls.trustAll()) {
                    sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
                }

                sslContext = sslContextBuilder.build();
            }
        } catch (SSLException e) {
            throw new WebClientException("An error occurred while creating ssl context.", e);
        }
        return Optional.of(sslContext);
    }

    private SslContext nettySslFromJavaNet(SSLContext javaNetContext) {
        return new JdkSslContext(
                javaNetContext, true, null,
                IdentityCipherSuiteFilter.INSTANCE, null,
                ClientAuth.OPTIONAL, null, false);
    }

    /**
     * Connection timeout duration.
     *
     * @return connection timeout
     */
    Duration connectTimeout() {
        return connectTimeout;
    }

    /**
     * Read timeout duration.
     *
     * @return read timeout
     */
    Duration readTimout() {
        return readTimeout;
    }

    /**
     * Configured proxy.
     *
     * @return proxy
     */
    Optional<Proxy> proxy() {
        return Optional.ofNullable(proxy);
    }

    /**
     * Returns true if client should follow redirection.
     *
     * @return follow redirection
     */
    boolean followRedirects() {
        return followRedirects;
    }

    /**
     * Max number of followed redirections.
     *
     * @return max redirections
     */
    int maxRedirects() {
        return maxRedirects;
    }

    /**
     * Default client headers.
     *
     * @return default headers
     */
    WebClientRequestHeaders headers() {
        return clientHeaders;
    }

    /**
     * Instance of {@link CookieManager}.
     *
     * @return cookie manager
     */
    CookieManager cookieManager() {
        return cookieManager;
    }

    /**
     * Returns user agent.
     *
     * @return user agent
     */
    String userAgent() {
        return userAgent.get();
    }

    WebClientTls tls() {
        return webClientTls;
    }

    Optional<Context> context() {
        return Optional.ofNullable(context);
    }

    Config config() {
        return config;
    }

    List<WebClientService> clientServices() {
        return clientServices;
    }

    MessageBodyReaderContext readerContext() {
        return readerContext;
    }

    MessageBodyWriterContext writerContext() {
        return writerContext;
    }

    URI uri() {
        return uri;
    }

    boolean keepAlive() {
        return keepAlive;
    }

    boolean validateHeaders() {
        return validateHeaders;
    }

    /**
     * A fluent API builder for {@link WebClientConfiguration}.
     */
    static class Builder<B extends Builder<B, T>, T extends WebClientConfiguration>
            implements io.helidon.common.Builder<T>,
                       ParentingMediaContextBuilder<B>,
                       MediaContextBuilder<B> {

        private final WebClientRequestHeaders clientHeaders;
        private final Map<String, String> defaultCookies;

        private Config config;
        private Context context;
        private CookieStore cookieStore;
        private CookiePolicy cookiePolicy;
        private int maxRedirects;
        private Duration connectTimeout;
        private Duration readTimeout;
        private boolean followRedirects;
        private LazyValue<String> userAgent;
        private Proxy proxy;
        private boolean enableAutomaticCookieStore;
        private boolean keepAlive;
        private WebClientTls webClientTls;
        private URI uri;
        private MessageBodyReaderContext readerContext;
        private MessageBodyWriterContext writerContext;
        private List<WebClientService> clientServices;
        private boolean validateHeaders;
        @SuppressWarnings("unchecked")
        private B me = (B) this;

        /**
         * Creates new instance of the builder.
         */
        Builder() {
            clientHeaders = new WebClientRequestHeadersImpl();
            defaultCookies = new HashMap<>();
            clientServices = new ArrayList<>();
        }

        @Override
        public T build() {
            return (T) new WebClientConfiguration(this);
        }

        /**
         * Sets new connection timeout of the request.
         *
         * @param connectTimeout new connection timeout
         * @return updated builder instance
         */
        public B connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return me;
        }

        /**
         * Sets new read timeout of the response.
         *
         * @param readTimeout new read timeout
         * @return updated builder instance
         */
        public B readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return me;
        }

        /**
         * Whether to follow any response redirections or not.
         *
         * @param followRedirects follow redirection
         * @return updated builder instance
         */
        public B followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return me;
        }

        /**
         * Sets new user agent of the request.
         *
         * @param userAgent user agent
         * @return updated builder instance
         */
        public B userAgent(String userAgent) {
            this.userAgent = LazyValue.create(() -> userAgent);
            return me;
        }

        /**
         * Sets new user agent wrapped by {@link LazyValue}.
         *
         * @param userAgent wrapped user agent
         * @return updated builder instance
         */
        public B userAgent(LazyValue<String> userAgent) {
            this.userAgent = userAgent;
            return me;
        }

        /**
         * Sets new request proxy.
         *
         * @param proxy request proxy
         * @return updated builder instance
         */
        public B proxy(Proxy proxy) {
            this.proxy = proxy;
            return me;
        }

        /**
         * New TLS configuration.
         *
         * @param webClientTls tls configuration
         * @return updated builder instance
         */
        public B tls(WebClientTls webClientTls) {
            this.webClientTls = webClientTls;
            return me;
        }

        /**
         * Sets max number of followed redirects.
         *
         * @param maxRedirects max redirects
         * @return updated builder instance
         */
        public B maxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
            return me;
        }

        /**
         * Sets default client request headers.
         *
         * Overrides previously set default client headers.
         *
         * @param clientHeaders default request headers
         * @return updated builder instance
         */
        public B clientHeaders(WebClientRequestHeaders clientHeaders) {
            this.clientHeaders.putAll(clientHeaders);
            return me;
        }

        /**
         * Sets new instance of {@link CookieStore} with default cookies.
         *
         * @param cookieStore cookie store
         * @return updated builder instance
         */
        public B cookieStore(CookieStore cookieStore) {
            this.cookieStore = cookieStore;
            return me;
        }

        /**
         * Sets new {@link CookiePolicy}.
         *
         * @param cookiePolicy cookie policy
         * @return updated builder instance
         */
        public B cookiePolicy(CookiePolicy cookiePolicy) {
            this.cookiePolicy = cookiePolicy;
            return me;
        }

        /**
         * Adds default cookie to every request.
         *
         * @param key   cookie name
         * @param value cookie value
         * @return updated builder instance
         */
        public B defaultCookie(String key, String value) {
            defaultCookies.put(key, value);
            return me;
        }

        /**
         * Adds default header to every request.
         *
         * @param key    header name
         * @param values header value
         * @return updated builder instance
         */
        public B defaultHeader(String key, List<String> values) {
            clientHeaders.put(key, values);
            return me;
        }

        @Override
        public B mediaContext(MediaContext mediaContext) {
            writerContextParent(mediaContext.writerContext());
            readerContextParent(mediaContext.readerContext());
            return me;
        }

        /**
         * Sets specific context in which all of the requests will be running.
         *
         * @return updated builder instance
         */
        public B context(Context context) {
            this.context = context;
            return me;
        }

        /**
         * Base uri for each request.
         *
         * @return updated builder instance
         */
        public B uri(URI uri) {
            this.uri = uri;
            return me;
        }

        /**
         * Whether to validate header names.
         * Defaults to {@code true}.
         *
         * @param validate whether to validate the header name contains only allowed characters
         * @return updated builder instance
         */
        B validateHeaders(boolean validate) {
            this.validateHeaders = validate;
            return me;
        }

        @Override
        public B addReader(MessageBodyReader<?> reader) {
            this.readerContext.registerReader(reader);
            return me;
        }

        @Override
        public B addStreamReader(MessageBodyStreamReader<?> streamReader) {
            this.readerContext.registerReader(streamReader);
            return me;
        }

        @Override
        public B addWriter(MessageBodyWriter<?> writer) {
            this.writerContext.registerWriter(writer);
            return me;
        }

        @Override
        public B addStreamWriter(MessageBodyStreamWriter<?> streamWriter) {
            this.writerContext.registerWriter(streamWriter);
            return me;
        }

        @Override
        public B addMediaSupport(MediaSupport mediaSupport) {
            Objects.requireNonNull(mediaSupport);
            mediaSupport.register(readerContext, writerContext);
            return me;
        }

        private B enableAutomaticCookieStore(Boolean enableAutomaticCookieStore) {
            this.enableAutomaticCookieStore = enableAutomaticCookieStore;
            return me;
        }

        B readerContextParent(MessageBodyReaderContext readerContext) {
            this.readerContext = MessageBodyReaderContext.create(readerContext);
            return me;
        }

        B writerContextParent(MessageBodyWriterContext writerContext) {
            this.writerContext = MessageBodyWriterContext.create(writerContext);
            return me;
        }

        B readerContext(MessageBodyReaderContext readerContext) {
            this.readerContext = readerContext;
            return me;
        }

        B writerContext(MessageBodyWriterContext writerContext) {
            this.writerContext = writerContext;
            return me;
        }

        B clientServices(List<WebClientService> clientServices) {
            this.clientServices = clientServices;
            return me;
        }

        B keepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
            return me;
        }

        /**
         * Configures this {@link WebClientConfiguration.Builder} from the supplied {@link Config}.
         * <table class="config">
         * <caption>Optional configuration parameters</caption>
         * <tr>
         *     <th>key</th>
         *     <th>description</th>
         * </tr>
         * <tr>
         *     <td>uri</td>
         *     <td>Basic uri for each client request</td>
         * </tr>
         * <tr>
         *     <td>connect-timeout-millis</td>
         *     <td>Request connection timeout</td>
         * </tr>
         * <tr>
         *     <td>read-timeout-millis</td>
         *     <td>Response read timeout</td>
         * </tr>
         * <tr>
         *     <td>follow-redirects</td>
         *     <td>Whether redirects should be followed or not</td>
         * </tr>
         * <tr>
         *     <td>max-redirects</td>
         *     <td>Max number of followed redirections</td>
         * </tr>
         * <tr>
         *     <td>user-agent</td>
         *     <td>Name of the user agent which should be used</td>
         * </tr>
         * <tr>
         *     <td>keep-alive</td>
         *     <td>Whether connection should be kept alive</td>
         * </tr>
         * <tr>
         *     <td>cookies</td>
         *     <td>Default cookies which should be used</td>
         * </tr>
         * <tr>
         *     <td>headers</td>
         *     <td>Default headers which should be used</td>
         * </tr>
         * <tr>
         *     <td>tls</td>
         *     <td>TLS configuration. See {@link WebClientTls.Builder#config(Config)}</td>
         * </tr>
         * <tr>
         *     <td>proxy</td>
         *     <td>Proxy configuration. See {@link Proxy.Builder#config(Config)}</td>
         * </tr>
         * </table>
         *
         * @param config config
         * @return updated builder instance
         */
        public B config(Config config) {
            this.config = config;
            // now for other options
            config.get("uri").asString().ifPresent(baseUri -> uri(URI.create(baseUri)));
            config.get("connect-timeout-millis").asLong().ifPresent(timeout -> connectTimeout(Duration.ofMillis(timeout)));
            config.get("read-timeout-millis").asLong().ifPresent(timeout -> readTimeout(Duration.ofMillis(timeout)));
            config.get("follow-redirects").asBoolean().ifPresent(this::followRedirects);
            config.get("max-redirects").asInt().ifPresent(this::maxRedirects);
            config.get("user-agent").asString().ifPresent(this::userAgent);
            config.get("keep-alive").asBoolean().ifPresent(this::keepAlive);
            config.get("cookies").asNode().ifPresent(this::cookies);
            config.get("headers").asNode().ifPresent(this::headers);
            DeprecatedConfig.get(config, "tls", "ssl")
                    .as(WebClientTls.builder()::config)
                    .map(WebClientTls.Builder::build)
                    .ifPresent(this::tls);
            config.get("proxy")
                    .as(Proxy.builder()::config)
                    .map(Proxy.Builder::build)
                    .ifPresent(this::proxy);
            config.get("media-support").as(MediaContext::create).ifPresent(this::mediaContext);
            return me;
        }

        /**
         * Updates builder existing client configuration.
         *
         * @param configuration client configuration
         * @return updated builder instance
         */
        public B update(WebClientConfiguration configuration) {
            connectTimeout(configuration.connectTimeout);
            readTimeout(configuration.readTimeout);
            followRedirects(configuration.followRedirects);
            userAgent(configuration.userAgent);
            proxy(configuration.proxy);
            tls(configuration.webClientTls);
            maxRedirects(configuration.maxRedirects);
            clientHeaders(configuration.clientHeaders);
            enableAutomaticCookieStore(configuration.enableAutomaticCookieStore);
            cookieStore(configuration.cookieManager.getCookieStore());
            cookiePolicy(configuration.cookiePolicy);
            clientServices(configuration.clientServices);
            readerContextParent(configuration.readerContext);
            writerContextParent(configuration.writerContext);
            context(configuration.context);
            keepAlive(configuration.keepAlive);
            validateHeaders(configuration.validateHeaders);
            configuration.cookieManager.defaultCookies().forEach(this::defaultCookie);
            config = configuration.config;

            return me;
        }

        private void headers(Config configHeaders) {
            configHeaders.asNodeList()
                    .ifPresent(headers -> headers
                            .forEach(header -> defaultHeader(header.get("name").asString().get(),
                                                             header.get("value").asList(String.class).get())));
        }

        private void cookies(Config cookies) {
            cookies.get("automatic-store-enabled").asBoolean().ifPresent(this::enableAutomaticCookieStore);
            Config map = cookies.get("default-cookies");
            map.asNodeList()
                    .ifPresent(headers -> headers
                            .forEach(header -> defaultCookie(header.get("name").asString().get(),
                                                             header.get("value").asString().get())));
        }

    }
}
