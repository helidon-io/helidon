/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.api;

import java.net.URI;
import java.util.ServiceLoader;

import io.helidon.builder.api.Prototype;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderValues;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.webclient.spi.DnsResolver;
import io.helidon.webclient.spi.DnsResolverProvider;

class HttpClientConfigSupport {
    private static final LazyValue<Tls> EMPTY_TLS = LazyValue.create(() -> Tls.builder().build());
    private static final LazyValue<DnsResolver> DISCOVERED_DNS_RESOLVER = LazyValue.create(() -> {
        return HelidonServiceLoader.builder(ServiceLoader.load(DnsResolverProvider.class))
                .addService(new DefaultDnsResolverProvider(), 20) // low weight
                .build()
                .asList()
                .get(0) // we are guaranteed to have an instance, as we add it a few lines above
                .createDnsResolver();
    });

    static class HttpCustomMethods {
        /**
         * Base URI of the client.
         *
         * @param builder builder to update
         * @param baseUri base URI to use, query is extracted to base query (if any)
         */
        @Prototype.BuilderMethod
        static void baseUri(HttpClientConfig.BuilderBase<?, ?> builder, URI baseUri) {
            builder.baseUri(ClientUri.create().resolve(baseUri));
        }

        /**
         * Base URI of the client.
         *
         * @param builder builder to update
         * @param baseUri base URI to use, query is extracted to base query (if any)
         */
        @Prototype.BuilderMethod
        static void baseUri(HttpClientConfig.BuilderBase<?, ?> builder, String baseUri) {
            baseUri(builder, URI.create(baseUri));
        }

        /**
         * Add a default header value.
         *
         * @param builder builder to update
         * @param name name of the header
         * @param value value of the header
         */
        @Prototype.BuilderMethod
        static void addHeader(HttpClientConfig.BuilderBase<?, ?> builder, HeaderName name, String value) {
            builder.addHeader(HeaderValues.create(name, value));
        }

        /**
         * Add a default header value.
         *
         * @param builder builder to update
         * @param name name of the header
         * @param value value of the header
         */
        @Prototype.BuilderMethod
        static void addHeader(HttpClientConfig.BuilderBase<?, ?> builder, HeaderName name, int value) {
            builder.addHeader(HeaderValues.create(name, value));
        }

        /**
         * Add a default header value.
         *
         * @param builder builder to update
         * @param name name of the header
         * @param value value of the header
         */
        @Prototype.BuilderMethod
        static void addHeader(HttpClientConfig.BuilderBase<?, ?> builder, HeaderName name, long value) {
            builder.addHeader(HeaderValues.create(name, value));
        }


        /**
         * Add default header value. This method is not optimal and should only be used when the header name is really
         * obtained from a string, in other cases, use an alternative with {@link io.helidon.http.HeaderName}
         * or {@link io.helidon.http.Header}.
         *
         * @param builder builder to update
         * @param name name of the header
         * @param value value of the header
         * @see #addHeader(io.helidon.http.Header)
         */
        @Prototype.BuilderMethod
        static void addHeader(HttpClientConfig.BuilderBase<?, ?> builder, String name, String value) {
            builder.addHeader(HeaderValues.create(name, value));
        }

        /**
         * Add default header value. This method is not optimal and should only be used when the header name is really
         * obtained from a string, in other cases, use an alternative with {@link io.helidon.http.HeaderName}
         * or {@link io.helidon.http.Header}.
         *
         * @param builder builder to update
         * @param name name of the header
         * @param value value of the header
         * @see #addHeader(io.helidon.http.Header)
         */
        @Prototype.BuilderMethod
        static void addHeader(HttpClientConfig.BuilderBase<?, ?> builder, String name, int value) {
            builder.addHeader(HeaderValues.create(name, value));
        }

        /**
         * Add default header value. This method is not optimal and should only be used when the header name is really
         * obtained from a string, in other cases, use an alternative with {@link io.helidon.http.HeaderName}
         * or {@link io.helidon.http.Header}.
         *
         * @param builder builder to update
         * @param name name of the header
         * @param value value of the header
         * @see #addHeader(io.helidon.http.Header)
         */
        @Prototype.BuilderMethod
        static void addHeader(HttpClientConfig.BuilderBase<?, ?> builder, String name, long value) {
            builder.addHeader(HeaderValues.create(name, value));
        }
    }

    static class HttpBuilderDecorator implements Prototype.BuilderDecorator<HttpClientConfig.BuilderBase<?, ?>>  {
        @Override
        public void decorate(HttpClientConfig.BuilderBase<?, ?> target) {
            if (target.tls().isEmpty()) {
                target.tls(EMPTY_TLS.get());
            }

            target.socketOptions(SocketOptions.builder()
                                         .update(it -> {
                                             target.socketOptions().ifPresent(it::from);
                                         })
                                         .update(it -> target.connectTimeout().ifPresent(it::connectTimeout))
                                         .update(it -> target.readTimeout().ifPresent(it::readTimeout))
                                         .build());

            if (target.dnsAddressLookup().isEmpty()) {
                target.dnsAddressLookup(DnsAddressLookup.defaultLookup());
            }
            if (target.dnsResolver().isEmpty()) {
                target.dnsResolver(DISCOVERED_DNS_RESOLVER.get());
            }

            target.defaultHeadersMap()
                    .forEach(target::addHeader);

            if (!target.mediaSupports().isEmpty()) {
                target.mediaContext(MediaContext.builder()
                                            .update(it -> target.mediaSupports().forEach(it::addMediaSupport))
                                            .fallback(target.mediaContext())
                                            .build());
            }

            if (target.contentEncoding().isEmpty()) {
                target.contentEncoding(ContentEncodingContext.create());
            }

            if (target.executor().isEmpty()) {
                target.executor(LoomClient.EXECUTOR.get());
            }

            if (target.proxy().isEmpty()) {
                target.proxy(Proxy.create());
            }
        }
    }
}
