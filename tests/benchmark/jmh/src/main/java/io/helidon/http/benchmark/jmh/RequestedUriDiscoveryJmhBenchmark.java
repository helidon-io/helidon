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

import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.HeaderNames;
import io.helidon.http.RequestedUriDiscoveryContext;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.WritableHeaders;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class RequestedUriDiscoveryJmhBenchmark {
    private final RequestedUriDiscoveryContext discoveryContext = RequestedUriDiscoveryContext.builder().build();
    private final InetSocketAddress remoteAddress = InetSocketAddress.createUnresolved("client", 8080);
    private final InetSocketAddress localAddress = InetSocketAddress.createUnresolved("localhost", 8080);

    @Param({
            "/private",
            "https://victim.example:9443/private"
    })
    private String requestTarget;
    private String requestPath;
    private String requestScheme;
    private ServerRequestHeaders headers;

    @Setup
    public void setup() {
        boolean absoluteRequestTarget = requestTarget.charAt(0) != '/';
        requestPath = absoluteRequestTarget ? "/private" : requestTarget;
        requestScheme = absoluteRequestTarget ? "https" : null;
        String authority = absoluteRequestTarget ? "victim.example:9443" : "public.example:8080";
        headers = ServerRequestHeaders.create(WritableHeaders.create().add(HeaderNames.HOST, authority));
    }

    @Benchmark
    public UriInfo discover() {
        if (requestScheme != null) {
            return discoveryContext.uriInfo(remoteAddress,
                                            localAddress,
                                            requestPath,
                                            requestScheme,
                                            headers,
                                            UriQuery.empty(),
                                            false);
        }
        return discoveryContext.uriInfo(remoteAddress,
                                        localAddress,
                                        requestPath,
                                        headers,
                                        UriQuery.empty(),
                                        false);
    }
}
