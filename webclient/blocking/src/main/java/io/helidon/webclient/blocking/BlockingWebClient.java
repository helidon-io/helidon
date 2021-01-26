/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.webclient.blocking;

import io.helidon.common.context.Context;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.media.common.MediaContext;
import io.helidon.media.common.MediaContextBuilder;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyStreamReader;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.common.ParentingMediaContextBuilder;
import io.helidon.webclient.Proxy;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientTls;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webclient.spi.WebClientServiceProvider;

import java.net.URI;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Base client which is used to perform requests.
 */
public class BlockingWebClient {

    private final WebClient webClient;


    public BlockingWebClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public static Builder builder(){
        return new Builder();
    }

    /**
     * Create a new WebClient.
     *
     * @return client
     */
    public static BlockingWebClient create() {

        return new BlockingWebClient(WebClient.create());
    }

    /**
     * Create a new WebClient based on {@link Config}.
     *
     * @param config client config
     * @return client
     */
    public static BlockingWebClient create(Config config) {
        return new BlockingWebClient(WebClient.create(config));
    }


    /**
     * Create a request builder for a put method.
     *
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder put() {
        return new BlockingWebClientRequestBuilderImpl(webClient.put());
    }

    /**
     * Create a request builder for a get method.
     *
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder get() {
        return new BlockingWebClientRequestBuilderImpl(webClient.get());
    }

    /**
     * Create a request builder for a post method.
     *
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder post() {
        return new BlockingWebClientRequestBuilderImpl(webClient.post());
    }

    /**
     * Create a request builder for a delete method.
     *
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder delete() {
        return new BlockingWebClientRequestBuilderImpl(webClient.delete());
    }

    /**
     * Create a request builder for a options method.
     *
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder options() {
        return new BlockingWebClientRequestBuilderImpl(webClient.options());
    }

    /**
     * Create a request builder for a trace method.
     *
     * @return client request builder
     */

    public BlockingWebClientRequestBuilder trace() {
        return new BlockingWebClientRequestBuilderImpl(webClient.trace());
    }

    /**
     * Create a request builder for a head method.
     *
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder head() {
        return new BlockingWebClientRequestBuilderImpl(webClient.head());
    }

    /**
     * Create a request builder for a method based on method parameter.
     *
     * @param method request method
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder method(String method) {
        return new BlockingWebClientRequestBuilderImpl(webClient.method(method));
    }

    /**
     * Create a request builder for a method based on method parameter.
     *
     * @param method request method
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder method(Http.Method method) {
        return new BlockingWebClientRequestBuilderImpl(webClient.method(method));
    }


    public static final class Builder implements io.helidon.common.Builder<BlockingWebClient>,
            ParentingMediaContextBuilder<BlockingWebClient.Builder>,
            MediaContextBuilder<BlockingWebClient.Builder> {

        private final WebClient.Builder builder;

        public Builder() {
            this.builder = WebClient.builder();
        }

        @Override
        public BlockingWebClient build() {
            return new BlockingWebClient(builder.build());
        }

        /**
         * Register new instance of {@link WebClientService}.
         *
         * @param service client service instance
         * @return updated builder instance
         */
        public BlockingWebClient.Builder addService(WebClientService service) {
            builder.addService(service);
            return this;
        }

        /**
         * Register new instance of {@link WebClientService}.
         *
         * @param serviceSupplier client service instance
         * @return updated builder instance
         */
        public BlockingWebClient.Builder addService(Supplier<? extends WebClientService> serviceSupplier) {
            return addService(serviceSupplier.get());
        }

        /**
         * Sets if Java Service loader should be used to load all {@link WebClientServiceProvider}.
         *
         * @param useServiceLoader whether to use the Java Service loader
         * @return updated builder instance
         */
        public BlockingWebClient.Builder useSystemServiceLoader(boolean useServiceLoader) {
            builder.useSystemServiceLoader(useServiceLoader);
            return this;
        }

        /**
         * Sets new proxy which will used for the requests.
         *
         * @param proxy proxy instance
         * @return updated builder instance
         */
        public BlockingWebClient.Builder proxy(Proxy proxy) {
            builder.proxy(proxy);
            return this;
        }

        @Override
        public BlockingWebClient.Builder mediaContext(MediaContext mediaContext) {
            builder.mediaContext(mediaContext);
            return this;
        }

        @Override
        public BlockingWebClient.Builder addMediaSupport(MediaSupport mediaSupport) {
            builder.addMediaSupport(mediaSupport);
            return this;
        }

        @Override
        public BlockingWebClient.Builder addReader(MessageBodyReader<?> reader) {
            builder.addReader(reader);
            return this;
        }

        @Override
        public BlockingWebClient.Builder addStreamReader(MessageBodyStreamReader<?> streamReader) {
            builder.addStreamReader(streamReader);
            return this;
        }

        @Override
        public BlockingWebClient.Builder addWriter(MessageBodyWriter<?> writer) {
            builder.addWriter(writer);
            return this;
        }

        @Override
        public BlockingWebClient.Builder addStreamWriter(MessageBodyStreamWriter<?> streamWriter) {
            builder.addStreamWriter(streamWriter);
            return this;
        }

        /**
         * Config of this client.
         *
         * @param config client config
         * @return updated builder instance
         */
        public BlockingWebClient.Builder config(Config config) {
            builder.config(config);
            return this;
        }

        /**
         * Sets new connection timeout.
         *
         * @param amount amount of time
         * @param unit   time unit
         * @return updated builder instance
         */
        public BlockingWebClient.Builder connectTimeout(long amount, TimeUnit unit) {
            builder.connectTimeout(amount, unit);
            return this;
        }

        /**
         * Sets new read timeout.
         *
         * @param amount amount of time
         * @param unit   time unit
         * @return updated builder instance
         */
        public BlockingWebClient.Builder readTimeout(long amount, TimeUnit unit) {
            builder.readTimeout(amount, unit);
            return this;
        }

        /**
         * Sets new {@link WebClientTls} instance which contains ssl configuration.
         *
         * @param webClientTls tls instance
         * @return updated builder instance
         */
        public BlockingWebClient.Builder tls(WebClientTls webClientTls) {
            builder.tls(webClientTls);
            return this;
        }

        /**
         * Sets specific context which should be used in requests.
         *
         * @param context context
         * @return updated builder instance
         */
        public BlockingWebClient.Builder context(Context context) {
            builder.context(context);
            return this;
        }

        /**
         * Add a default cookie.
         *
         * @param name  cookie name
         * @param value cookie value
         * @return updated builder instance
         */
        public BlockingWebClient.Builder addCookie(String name, String value) {
            builder.addCookie(name, value);
            return this;
        }

        /**
         * Add a default header (such as accept).
         *
         * @param header header name
         * @param value  header values
         * @return updated builder instance
         */
        public BlockingWebClient.Builder addHeader(String header, String... value) {
            builder.addHeader(header, value);
            return this;
        }

        /**
         * Sets base uri for each request.
         *
         * @param uri base uri
         * @return updated builder instance
         */
        public BlockingWebClient.Builder baseUri(URI uri) {
            builder.baseUri(uri);
            return this;
        }

        /**
         * Sets base uri for each request.
         *
         * @param uri base uri
         * @return updated builder instance
         */
        public BlockingWebClient.Builder baseUri(String uri) {
            return baseUri(URI.create(uri));
        }

        /**
         * Sets base url for each request.
         *
         * @param url base url
         * @return updated builder instance
         */
        public BlockingWebClient.Builder baseUri(URL url) {
            builder.baseUri(url);
            return this;
        }

        /**
         * Sets if redirects should be followed or not.
         *
         * @param follow follow redirects
         * @return updated builder instance
         */
        public BlockingWebClient.Builder followRedirects(boolean follow) {
            builder.followRedirects(follow);
            return this;
        }

        /**
         * Sets user agent name.
         *
         * @param userAgent user agent
         * @return updated builder instance
         */
        public BlockingWebClient.Builder userAgent(String userAgent) {
            builder.userAgent(userAgent);
            return this;
        }

        /**
         * Set whether connection to server should be kept alive after request.
         * This also sets header {@link io.helidon.common.http.Http.Header#CONNECTION} to {@code keep-alive}.
         *
         * @param keepAlive keep connection alive
         * @return updated builder instance
         */
        public BlockingWebClient.Builder keepAlive(boolean keepAlive) {
            builder.keepAlive(keepAlive);
            return this;
        }

        /**
         * Whether to validate header names.
         * Defaults to {@code true}.
         *
         * @param validate whether to validate the header name contains only allowed characters
         * @return updated builder instance
         */
        public BlockingWebClient.Builder validateHeaders(boolean validate) {
            builder.validateHeaders(validate);
            return this;
        }

    }
}
