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
import io.helidon.webclient.api.ClientUri;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class ClientUriJmhTest {
    private static final String HOST = "service.example";
    private static final URI LOWERCASE_URI = URI.create("https://service.example/path");
    private static final URI UPPERCASE_URI = URI.create("HTTPS://service.example/path");

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
    public UriInfo buildLowercaseImplicitPort() {
        return UriInfo.builder().scheme("https").host(HOST).build();
    }

    @Benchmark
    public UriInfo buildUppercaseImplicitPort() {
        return UriInfo.builder().scheme("HTTPS").host(HOST).build();
    }
}
