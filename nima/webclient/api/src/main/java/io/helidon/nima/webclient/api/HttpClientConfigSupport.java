package io.helidon.nima.webclient.api;

import java.net.URI;
import java.util.ServiceLoader;

import io.helidon.builder.api.Prototype;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.http.Http;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webclient.spi.DnsResolver;
import io.helidon.nima.webclient.spi.DnsResolverProvider;

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
         * Set a default header value.
         *
         * @param builder builder to update
         * @param name name of the header
         * @param value value of the header
         */
        @Prototype.BuilderMethod
        static void addHeader(HttpClientConfig.BuilderBase<?, ?> builder, Http.HeaderName name, String value) {
            builder.addHeader(Http.Header.create(name, value));
        }

        /**
         * Set default header value. This method is not optimal and should only be used when the header name is really
         * obtained from a string, in other cases, use an alternative with {@link io.helidon.common.http.Http.HeaderName}
         * or {@link io.helidon.common.http.Http.HeaderValue}.
         *
         * @param builder builder to update
         * @param name name of the header
         * @param value value of the header
         * @see #addHeader(Http.HeaderValue)
         */
        @Prototype.BuilderMethod
        static void addHeader(HttpClientConfig.BuilderBase<?, ?> builder, String name, String value) {
            builder.addHeader(Http.Header.create(Http.Header.create(name), value));
        }
    }

    static class HttpBuilderInterceptor implements Prototype.BuilderInterceptor<HttpClientConfig.BuilderBase<?, ?>>  {
        @Override
        public HttpClientConfig.BuilderBase<?, ?> intercept(HttpClientConfig.BuilderBase<?, ?> target) {
            if (target.tls() == null) {
                target.tls(EMPTY_TLS.get());
            }

            target.socketOptions(SocketOptions.builder()
                                         .update(it -> {
                                             if (target.socketOptions() != null) {
                                                 it.from(target.socketOptions());
                                             }
                                         })
                                         .update(it -> target.connectTimeout().ifPresent(it::connectTimeout))
                                         .update(it -> target.readTimeout().ifPresent(it::readTimeout))
                                         .build());

            if (target.dnsAddressLookup() == null) {
                target.dnsAddressLookup(DnsAddressLookup.defaultLookup());
            }
            if (target.dnsResolver() == null) {
                target.dnsResolver(DISCOVERED_DNS_RESOLVER.get());
            }

            target.defaultHeadersMap()
                    .forEach((key, value) -> target.addHeader(Http.Header.create(Http.Header.create(key), value)));

            if (target.mediaContext() == null) {
                if (target.mediaSupports().isEmpty()) {
                    target.mediaContext(MediaContext.create());
                } else {
                    target.mediaContext(MediaContext.builder()
                                                .update(it -> target.mediaSupports().forEach(it::addMediaSupport))
                                                .build());
                }
            } else {
                if (!target.mediaSupports().isEmpty()) {
                    target.mediaContext(MediaContext.builder()
                                                .update(it -> target.mediaSupports().forEach(it::addMediaSupport))
                                                .fallback(target.mediaContext())
                                                .build());
                }
            }

            if (target.contentEncoding() == null) {
                target.contentEncoding(ContentEncodingContext.create());
            }

            if (target.executor() == null) {
                target.executor(LoomClient.EXECUTOR.get());
            }

            if (target.proxy() == null) {
                target.proxy(Proxy.noProxy());
            }

            return target;
        }
    }
}
