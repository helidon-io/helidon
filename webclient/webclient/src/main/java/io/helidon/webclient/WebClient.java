/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.common.context.Context;
import io.helidon.common.http.Http;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.media.common.MediaContext;
import io.helidon.media.common.MediaContextBuilder;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyStreamReader;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.common.ParentingMediaContextBuilder;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webclient.spi.WebClientServiceProvider;

/**
 * Base client which is used to perform requests.
 */
public interface WebClient {

    /**
     * Create a new WebClient.
     *
     * @return client
     */
    static WebClient create() {
        return builder().build();
    }

    /**
     * Create a new WebClient based on {@link Config}.
     *
     * @param config client config
     * @return client
     */
    static WebClient create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Fluent API builder for client.
     *
     * @return client builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a request builder for a put method.
     *
     * @return client request builder
     */
    WebClientRequestBuilder put();

    /**
     * Create a request builder for a get method.
     *
     * @return client request builder
     */
    WebClientRequestBuilder get();

    /**
     * Create a request builder for a post method.
     *
     * @return client request builder
     */
    WebClientRequestBuilder post();

    /**
     * Create a request builder for a delete method.
     *
     * @return client request builder
     */
    WebClientRequestBuilder delete();

    /**
     * Create a request builder for a options method.
     *
     * @return client request builder
     */
    WebClientRequestBuilder options();

    /**
     * Create a request builder for a trace method.
     *
     * @return client request builder
     */
    WebClientRequestBuilder trace();

    /**
     * Create a request builder for a head method.
     *
     * @return client request builder
     */
    WebClientRequestBuilder head();

    /**
     * Create a request builder for a method based on method parameter.
     *
     * @param method request method
     * @return client request builder
     */
    WebClientRequestBuilder method(String method);

    /**
     * Create a request builder for a method based on method parameter.
     *
     * @param method request method
     * @return client request builder
     */
    WebClientRequestBuilder method(Http.RequestMethod method);

    final class Builder implements io.helidon.common.Builder<WebClient>,
                                   ParentingMediaContextBuilder<Builder>,
                                   MediaContextBuilder<Builder> {

        private final WebClientConfiguration.Builder<?, ?> configuration = NettyClient.SHARED_CONFIGURATION.get().derive();
        private final HelidonServiceLoader.Builder<WebClientServiceProvider> services = HelidonServiceLoader
                .builder(ServiceLoader.load(WebClientServiceProvider.class));
        private final List<WebClientService> webClientServices = new ArrayList<>();

        private Config config = Config.empty();

        private Builder() {
        }

        @Override
        public WebClient build() {
            return new NettyClient(this);
        }

        /**
         * Register new instance of {@link WebClientService}.
         *
         * @param service client service instance
         * @return updated builder instance
         */
        public Builder addService(WebClientService service) {
            webClientServices.add(service);
            return this;
        }

        /**
         * Register new instance of {@link WebClientService}.
         *
         * @param serviceSupplier client service instance
         * @return updated builder instance
         */
        public Builder addService(Supplier<? extends WebClientService> serviceSupplier) {
            return addService(serviceSupplier.get());
        }

        /**
         * Sets if Java Service loader should be used to load all {@link WebClientServiceProvider}.
         *
         * @param useServiceLoader whether to use the Java Service loader
         * @return updated builder instance
         */
        public Builder useSystemServiceLoader(boolean useServiceLoader) {
            services.useSystemServiceLoader(useServiceLoader);
            return this;
        }

        /**
         * Sets new proxy which will used for the requests.
         *
         * @param proxy proxy instance
         * @return updated builder instance
         */
        public Builder proxy(Proxy proxy) {
            this.configuration.proxy(proxy);
            return this;
        }

        @Override
        public Builder mediaContext(MediaContext mediaContext) {
            configuration.mediaContext(mediaContext);
            return this;
        }

        @Override
        public Builder addMediaSupport(MediaSupport mediaSupport) {
            configuration.addMediaSupport(mediaSupport);
            return this;
        }

        @Override
        public Builder addReader(MessageBodyReader<?> reader) {
            configuration.addReader(reader);
            return this;
        }

        @Override
        public Builder addStreamReader(MessageBodyStreamReader<?> streamReader) {
            configuration.addStreamReader(streamReader);
            return this;
        }

        @Override
        public Builder addWriter(MessageBodyWriter<?> writer) {
            configuration.addWriter(writer);
            return this;
        }

        @Override
        public Builder addStreamWriter(MessageBodyStreamWriter<?> streamWriter) {
            configuration.addStreamWriter(streamWriter);
            return this;
        }

        /**
         * Config of this client.
         *
         * @param config client config
         * @return updated builder instance
         */
        public Builder config(Config config) {
            this.config = config;
            configuration.config(config);
            return this;
        }

        /**
         * Sets new connection timeout.
         *
         * @param amount amount of time
         * @param unit   time unit
         * @return updated builder instance
         */
        public Builder connectTimeout(long amount, TimeUnit unit) {
            configuration.connectTimeout(Duration.of(amount, unit.toChronoUnit()));
            return this;
        }

        /**
         * Sets new read timeout.
         *
         * @param amount amount of time
         * @param unit   time unit
         * @return updated builder instance
         */
        public Builder readTimeout(long amount, TimeUnit unit) {
            configuration.readTimeout(Duration.of(amount, unit.toChronoUnit()));
            return this;
        }

        /**
         * Sets new {@link WebClientTls} instance which contains ssl configuration.
         *
         * @param webClientTls tls instance
         * @return updated builder instance
         */
        public Builder tls(WebClientTls webClientTls) {
            configuration.tls(webClientTls);
            return this;
        }

        /**
         * Sets specific context which should be used in requests.
         *
         * @param context context
         * @return updated builder instance
         */
        public Builder context(Context context) {
            configuration.context(context);
            return this;
        }

        /**
         * Add a default cookie.
         *
         * @param name  cookie name
         * @param value cookie value
         * @return updated builder instance
         */
        public Builder addCookie(String name, String value) {
            configuration.defaultCookie(name, value);
            return this;
        }

        /**
         * Add a default header (such as accept).
         *
         * @param header header name
         * @param value  header values
         * @return updated builder instance
         */
        public Builder addHeader(String header, String... value) {
            configuration.defaultHeader(header, Arrays.asList(value));
            return this;
        }

        /**
         * Sets base uri for each request.
         *
         * @param uri base uri
         * @return updated builder instance
         */
        public Builder baseUri(URI uri) {
            configuration.uri(uri);
            return this;
        }

        /**
         * Sets base uri for each request.
         *
         * @param uri base uri
         * @return updated builder instance
         */
        public Builder baseUri(String uri) {
            return baseUri(URI.create(uri));
        }

        /**
         * Sets base url for each request.
         *
         * @param url base url
         * @return updated builder instance
         */
        public Builder baseUri(URL url) {
            try {
                return baseUri(url.toURI());
            } catch (URISyntaxException e) {
                throw new WebClientException("Failed to create URI from URL", e);
            }
        }

        /**
         * Sets if redirects should be followed or not.
         *
         * @param follow follow redirects
         * @return updated builder instance
         */
        public Builder followRedirects(boolean follow) {
            configuration.followRedirects(follow);
            return this;
        }

        /**
         * Sets user agent name.
         *
         * @param userAgent user agent
         * @return updated builder instance
         */
        public Builder userAgent(String userAgent) {
            configuration.userAgent(userAgent);
            return this;
        }

        /**
         * Set whether connection to server should be kept alive after request.
         * This also sets header {@link io.helidon.common.http.Http.Header#CONNECTION} to {@code keep-alive}.
         *
         * @param keepAlive keep connection alive
         * @return updated builder instance
         */
        public Builder keepAlive(boolean keepAlive) {
            configuration.keepAlive(keepAlive);
            return this;
        }

        /**
         * Whether to validate header names.
         * Defaults to {@code true}.
         *
         * @param validate whether to validate the header name contains only allowed characters
         * @return updated builder instance
         */
        public Builder validateHeaders(boolean validate) {
            configuration.validateHeaders(validate);
            return this;
        }

        WebClientConfiguration configuration() {
            configuration.clientServices(services());
            return configuration.build();
        }

        private List<WebClientService> services() {
            Config servicesConfig = config.get("services");

            services.build()
                    .asList()
                    .forEach(provider -> {
                        Config providerConfig = servicesConfig.get(provider.configKey());
                        if (providerConfig.exists()) {
                            addService(provider.create(providerConfig));
                        }
                    });

            return webClientServices;
        }

    }
}
