/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.helidon.common.LazyValue;
import io.helidon.common.http.Headers;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.socket.SocketOptions;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webclient.spi.DnsResolver;

/**
 * Base class for HTTP implementations of {@link io.helidon.nima.webclient.WebClient}.
 */
public class LoomClient implements WebClient {
    private static final LazyValue<Tls> EMPTY_TLS = LazyValue.create(() -> Tls.builder().build());
    private static final SocketOptions EMPTY_OPTIONS = SocketOptions.builder().build();
    private static final LazyValue<ExecutorService> EXECUTOR = LazyValue.create(() -> {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                                                          .inheritInheritableThreadLocals(false)
                                                          .factory());
    });
    private final URI uri;
    private final Tls tls;
    private final SocketOptions channelOptions;
    private final DnsResolver dnsResolver;
    private final DnsAddressLookup dnsAddressLookup;
    private final int maxRedirects;
    private final boolean followRedirects;
    private final Headers defaultHeaders;

    /**
     * Construct this instance from a subclass of builder.
     *
     * @param builder builder the subclass is built from
     */
    protected LoomClient(WebClient.Builder<?, ?> builder) {
        this.uri = builder.baseUri();
        this.tls = builder.tls() == null ? EMPTY_TLS.get() : builder.tls();
        this.channelOptions = builder.channelOptions() == null ? EMPTY_OPTIONS : builder.channelOptions();
        this.dnsResolver = builder.dnsResolver();
        this.dnsAddressLookup = builder.dnsAddressLookup();
        this.maxRedirects = builder.maxRedirect();
        this.followRedirects = builder.followRedirect();
        this.defaultHeaders = builder.defaultHeaders();
    }

    /**
     * Base URI of this client.
     *
     * @return URI to use
     */
    public URI uri() {
        return uri;
    }

    /**
     * Executor services, uses virtual threads.
     *
     * @return executor service
     */
    public ExecutorService executor() {
        return EXECUTOR.get();
    }

    /**
     * TLS configuration for this client.
     *
     * @return TLS configuration
     */
    public Tls tls() {
        return tls;
    }

    /**
     * Socket options for this client.
     *
     * @return socket options
     */
    public SocketOptions socketOptions() {
        return channelOptions;
    }

    /**
     * DNS resolver instance to be used for this client.
     *
     * @return dns resolver instance
     */
    public DnsResolver dnsResolver() {
        return dnsResolver;
    }

    /**
     * DNS address lookup instance to be used for this client.
     *
     * @return DNS address lookup instance type
     */
    public DnsAddressLookup dnsAddressLookup() {
        return dnsAddressLookup;
    }

    /**
     * Whether to follow redirects.
     *
     * @return follow redirects
     */
    public boolean followRedirects() {
        return followRedirects;
    }

    /**
     * Maximum number of redirects allowed.
     *
     * @return allowed number of redirects
     */
    public int maxRedirects() {
        return maxRedirects;
    }

    /**
     * Default headers to be used in every request performed by this client.
     *
     * @return default headers
     */
    public Headers defaultHeaders() {
        return defaultHeaders;
    }

}
