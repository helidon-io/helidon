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

package io.helidon.webclient.benchmark.jmh;

import java.net.URI;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import io.helidon.http.Method;
import io.helidon.webclient.api.ClientRequest.OutputStreamHandler;
import io.helidon.webclient.api.ClientRequestBase;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClientConfig;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(3)
@Threads(1)
@State(Scope.Thread)
public class ClientRequestQueryBenchmark {
    private static final URI BASE_URI = URI.create("https://base.example/root?inherited=base");
    private static final WebClientConfig CLIENT_CONFIG = WebClientConfig.create();

    @Param({"1", "4"})
    public int queryParamCount;

    @Benchmark
    public String nonTemplate() {
        FakeClientRequest request = newRequest();
        request.uri(URI.create("https://base.example/items"));
        addQueryParams(request);
        return request.resolvedUri().pathWithQueryAndFragment();
    }

    @Benchmark
    public String relativeTemplate() {
        FakeClientRequest request = newRequest();
        request.uri("/items/{id}");
        request.pathParam("id", "one");
        addQueryParams(request);
        return request.resolvedUri().pathWithQueryAndFragment();
    }

    @Benchmark
    public String relativeTemplateSkipEncoding() {
        FakeClientRequest request = newRequest();
        request.uri("/items/{id}");
        request.pathParam("id", "one");
        request.skipUriEncoding(true);
        addQueryParams(request);
        return request.resolvedUri().pathWithQueryAndFragment();
    }

    @Benchmark
    public String absoluteTemplate() {
        FakeClientRequest request = newRequest();
        request.uri("https://{host}/items/{id}?template=value");
        request.pathParam("host", "other.example");
        request.pathParam("id", "one");
        addQueryParams(request);
        return request.resolvedUri().pathWithQueryAndFragment();
    }

    private FakeClientRequest newRequest() {
        return new FakeClientRequest(ClientUri.create(BASE_URI));
    }

    private void addQueryParams(FakeClientRequest request) {
        for (int i = 0; i < queryParamCount; i++) {
            request.queryParam("request" + i, "value" + i);
        }
    }

    private static final class FakeClientRequest extends ClientRequestBase<FakeClientRequest, HttpClientResponse> {
        private FakeClientRequest(ClientUri clientUri) {
            super(CLIENT_CONFIG, null, "jmh", Method.GET, clientUri, Collections.emptyMap());
        }

        @Override
        protected HttpClientResponse doSubmit(Object entity) {
            return null;
        }

        @Override
        protected HttpClientResponse doOutputStream(OutputStreamHandler outputStreamHandler) {
            return null;
        }
    }
}
