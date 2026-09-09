/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j.providers.cohere;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

final class CohereHttpClientSupport {
    private static final ProxySelector NO_PROXY_SELECTOR = new FixedProxySelector(Proxy.NO_PROXY);

    private CohereHttpClientSupport() {
    }

    static HttpClientBuilder create(Proxy proxy) {
        if (proxy.type() == Proxy.Type.SOCKS) {
            return new SocksHttpClientBuilder(proxy);
        }

        var proxySelector = switch (proxy.type()) {
            case DIRECT -> NO_PROXY_SELECTOR;
            case HTTP -> httpProxySelector(proxy);
            case SOCKS -> throw new IllegalStateException("SOCKS proxy was not handled");
        };

        return new ProxyJdkHttpClientBuilder(proxySelector);
    }

    static boolean isProxyAdapter(HttpClientBuilder httpClientBuilder) {
        return httpClientBuilder instanceof ProxyAdapter;
    }

    private static ProxySelector httpProxySelector(Proxy proxy) {
        if (proxy.address() instanceof InetSocketAddress address) {
            return ProxySelector.of(address);
        }
        throw new IllegalArgumentException("HTTP proxy address must be an InetSocketAddress");
    }

    private interface ProxyAdapter {
    }

    private static final class ProxyJdkHttpClientBuilder extends JdkHttpClientBuilder implements ProxyAdapter {
        private ProxyJdkHttpClientBuilder(ProxySelector proxySelector) {
            httpClientBuilder(java.net.http.HttpClient.newBuilder().proxy(proxySelector));
        }
    }

    private static final class SocksHttpClientBuilder implements HttpClientBuilder, ProxyAdapter {
        private final Proxy proxy;
        private Duration connectTimeout;
        private Duration readTimeout;

        private SocksHttpClientBuilder(Proxy proxy) {
            this.proxy = proxy;
        }

        @Override
        public Duration connectTimeout() {
            return connectTimeout;
        }

        @Override
        public HttpClientBuilder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        @Override
        public Duration readTimeout() {
            return readTimeout;
        }

        @Override
        public HttpClientBuilder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        @Override
        public HttpClient build() {
            var builder = new OkHttpClient.Builder()
                    .proxy(proxy);
            if (connectTimeout != null) {
                builder.connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);
            }
            if (readTimeout != null) {
                builder.readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS);
                builder.callTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS);
                builder.writeTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS);
            }
            return new SocksHttpClient(builder.build());
        }
    }

    private static final class SocksHttpClient implements HttpClient {
        private static final MediaType JSON = MediaType.get("application/json");
        private static final RequestBody EMPTY_BODY = RequestBody.create(new byte[0]);

        private final OkHttpClient client;

        private SocksHttpClient(OkHttpClient client) {
            this.client = client;
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) throws HttpException {
            var requestBuilder = new Request.Builder()
                    .url(request.url());
            request.headers().forEach((name, values) -> values.forEach(value -> requestBuilder.addHeader(name, value)));

            RequestBody body = request.body() == null ? null : RequestBody.create(request.body(), JSON);
            switch (request.method()) {
            case GET -> requestBuilder.get();
            case POST -> requestBuilder.post(body == null ? EMPTY_BODY : body);
            case DELETE -> {
                if (body == null) {
                    requestBuilder.delete();
                } else {
                    requestBuilder.delete(body);
                }
            }
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + request.method());
            }

            try (var response = client.newCall(requestBuilder.build()).execute()) {
                var responseBody = response.body();
                if (!response.isSuccessful()) {
                    throw new HttpException(response.code(), responseBody == null ? null : responseBody.string());
                }
                return SuccessfulHttpResponse.builder()
                        .statusCode(response.code())
                        .headers(response.headers().toMultimap())
                        .body(responseBody == null ? null : responseBody.bytes())
                        .build();
            } catch (SocketTimeoutException e) {
                throw new TimeoutException(e);
            } catch (IOException e) {
                throw new LangChain4jException(e);
            }
        }

        @Override
        public void execute(HttpRequest request,
                            ServerSentEventParser parser,
                            ServerSentEventListener listener) {
            throw new UnsupportedOperationException("Server-sent events are not used by the Cohere scoring model");
        }
    }

    private static final class FixedProxySelector extends ProxySelector {
        private final List<Proxy> proxies;

        private FixedProxySelector(Proxy proxy) {
            this.proxies = List.of(proxy);
        }

        @Override
        public List<Proxy> select(URI uri) {
            return proxies;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress socketAddress, IOException failure) {
            // A fixed selector has no proxy state to update.
        }
    }
}
