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

package io.helidon.webclient.http2;

import java.net.URI;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.api.ClientUri;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class Http2ClientRequestTargetBenchmark {
    @Param
    private Fragment fragment;

    private ClientRequestHeaders headers;
    private ClientUri uri;

    @Setup
    public void setup() {
        String fragmentPart = fragment == Fragment.PRESENT ? "#fragment" : "";
        uri = ClientUri.create(URI.create("http://localhost/test?query=value" + fragmentPart));
        headers = ClientRequestHeaders.create(WritableHeaders.create());
    }

    @Benchmark
    public Http2Headers prepareHeaders() {
        return Http2CallChainBase.prepareHeaders(Method.GET, headers, uri);
    }

    public enum Fragment {
        ABSENT,
        PRESENT
    }
}
