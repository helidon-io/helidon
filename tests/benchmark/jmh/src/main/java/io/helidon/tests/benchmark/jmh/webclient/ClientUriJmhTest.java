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

package io.helidon.tests.benchmark.jmh.webclient;

import java.net.URI;

import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.webclient.api.ClientUri;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class ClientUriJmhTest {
    private static final String HOST = "service.example";
    private static final URI LOWERCASE_URI = URI.create("https://service.example/path");
    private static final URI UPPERCASE_URI = URI.create("HTTPS://service.example/path");
    private static final URI REGISTERED_NAME_URI = URI.create("https://service_1.example:8443/path");

    private ClientUri lowercaseClientUri;

    @Setup
    public void setup() {
        lowercaseClientUri = ClientUri.create(LOWERCASE_URI);
    }

    @Benchmark
    public ClientUri copyLowercaseClientUri() {
        return ClientUri.create(lowercaseClientUri);
    }

    @Benchmark
    public ClientUri ingestUppercaseUri() {
        return ClientUri.create(UPPERCASE_URI);
    }

    @Benchmark
    public ClientUri ingestLowercaseUri() {
        return ClientUri.create(LOWERCASE_URI);
    }

    @Benchmark
    public ClientUri ingestRegisteredNameUri() {
        return ClientUri.create(REGISTERED_NAME_URI);
    }

    @Benchmark
    public UriInfo buildLowercaseImplicitPort() {
        return UriInfo.builder().scheme("https").host(HOST).build();
    }

    @Benchmark
    public UriInfo buildUppercaseImplicitPort() {
        return UriInfo.builder().scheme("HTTPS").host(HOST).build();
    }

    @Benchmark
    public String importUriAndSerialize(RequestTargetState state) {
        return ClientUri.create(state.uri).pathWithQueryAndFragment();
    }

    @Benchmark
    public String importUriInfoAndSerialize(RequestTargetState state) {
        return ClientUri.create(state.uriInfo).pathWithQueryAndFragment();
    }

    @Benchmark
    public String copyAndSerialize(RequestTargetState state) {
        return ClientUri.create(state.clientUri).pathWithQueryAndFragment();
    }

    @Benchmark
    public String mutateAndSerialize(RequestTargetState state) {
        var uri = ClientUri.create(state.clientUri);
        uri.writeableQuery().add("page", "2");
        return uri.pathWithQueryAndFragment();
    }

    @State(Scope.Thread)
    public static class RequestTargetState {
        @Param({"path", "shortQuery", "signedQuery"})
        public String target;

        private URI uri;
        private UriInfo uriInfo;
        private ClientUri clientUri;

        @Setup
        public void setup() {
            String requestTarget = switch (target) {
                case "path" -> "/api/items";
                case "shortQuery" -> "/api/items?limit=10&sort=name";
                case "signedQuery" -> "/bucket/a%2fb/%7euser/report%20one"
                        + "?X-Amz-Algorithm=AWS4-HMAC-SHA256"
                        + "&X-Amz-Credential=example%2f20261105%2fus-east-1%2fs3%2faws4_request"
                        + "&X-Amz-Date=20261105T120000Z&X-Amz-Expires=900"
                        + "&X-Amz-SignedHeaders=host&tag=a+b&flag&tag=a%20b"
                        + "&X-Amz-Signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
                default -> throw new IllegalArgumentException("Unknown request target: " + target);
            };
            uri = URI.create("https://" + HOST + requestTarget);
            uriInfo = UriInfo.builder()
                    .scheme("https")
                    .host(HOST)
                    .path(UriPath.create(uri.getRawPath()))
                    .query(UriQuery.create(uri))
                    .build();
            clientUri = ClientUri.create(uri);
        }
    }
}
