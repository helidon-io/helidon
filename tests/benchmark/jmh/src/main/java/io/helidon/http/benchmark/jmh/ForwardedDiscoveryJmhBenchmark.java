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

package io.helidon.http.benchmark.jmh;

import java.net.InetSocketAddress;

import io.helidon.common.configurable.AllowList;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.HeaderNames;
import io.helidon.http.RequestedUriDiscoveryContext;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.WritableHeaders;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class ForwardedDiscoveryJmhBenchmark {
    private final RequestedUriDiscoveryContext discoveryContext = RequestedUriDiscoveryContext.builder()
            .enabled(true)
            .addDiscoveryType(RequestedUriDiscoveryContext.RequestedUriDiscoveryType.FORWARDED)
            .trustedProxies(AllowList.builder().allowAll(true).build())
            .build();
    private final RequestedUriDiscoveryContext xForwardedDiscoveryContext = RequestedUriDiscoveryContext.builder()
            .enabled(true)
            .addDiscoveryType(RequestedUriDiscoveryContext.RequestedUriDiscoveryType.X_FORWARDED)
            .trustedProxies(AllowList.builder().allowAll(true).build())
            .build();
    private final InetSocketAddress remoteAddress = InetSocketAddress.createUnresolved("client", 8080);
    private final InetSocketAddress localAddress = InetSocketAddress.createUnresolved("localhost", 8080);
    private final ServerRequestHeaders absentHeaders = ServerRequestHeaders.create(WritableHeaders.create()
            .add(HeaderNames.HOST, "backend.example"));
    private final ServerRequestHeaders protoOnlyHeaders = ServerRequestHeaders.create(WritableHeaders.create()
            .add(HeaderNames.HOST, "backend.example")
            .add(HeaderNames.FORWARDED, "for=client.example;proto=https"));
    private final ServerRequestHeaders hostHeaders = ServerRequestHeaders.create(WritableHeaders.create()
            .add(HeaderNames.HOST, "backend.example:8080")
            .add(HeaderNames.FORWARDED, "for=client.example;host=\"public.example:8443\";proto=https"));
    private final ServerRequestHeaders xForwardedHeaders = ServerRequestHeaders.create(WritableHeaders.create()
            .add(HeaderNames.HOST, "backend.example")
            .add(HeaderNames.X_FORWARDED_FOR, "client.example, proxy.example")
            .add(HeaderNames.X_FORWARDED_PROTO, "https"));
    private final String requestPath = "/private";
    private final String requestScheme = "http";

    @Benchmark
    public UriInfo absent() {
        return discoveryContext.uriInfo(remoteAddress,
                                        localAddress,
                                        requestPath,
                                        requestScheme,
                                        absentHeaders,
                                        UriQuery.empty(),
                                        false);
    }

    @Benchmark
    public UriInfo protoOnly() {
        return discoveryContext.uriInfo(remoteAddress,
                                        localAddress,
                                        requestPath,
                                        requestScheme,
                                        protoOnlyHeaders,
                                        UriQuery.empty(),
                                        false);
    }

    @Benchmark
    public UriInfo host() {
        return discoveryContext.uriInfo(remoteAddress,
                                        localAddress,
                                        requestPath,
                                        requestScheme,
                                        hostHeaders,
                                        UriQuery.empty(),
                                        false);
    }

    @Benchmark
    public UriInfo xForwarded() {
        return xForwardedDiscoveryContext.uriInfo(remoteAddress,
                                                  localAddress,
                                                  requestPath,
                                                  requestScheme,
                                                  xForwardedHeaders,
                                                  UriQuery.empty(),
                                                  false);
    }
}
