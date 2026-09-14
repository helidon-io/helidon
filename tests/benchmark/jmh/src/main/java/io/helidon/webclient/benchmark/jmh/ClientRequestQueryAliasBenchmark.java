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
public class ClientRequestQueryAliasBenchmark {
    private static final URI BASE_URI = URI.create("https://base.example/root");
    private static final WebClientConfig CLIENT_CONFIG = WebClientConfig.create();

    @Param({"4", "32"})
    public int queryParamCount;

    @Benchmark
    public String encodedAliasReplay() {
        StringBuilder template = new StringBuilder("https://{host}/items/{id}?");
        for (int i = 0; i < queryParamCount; i++) {
            if (i > 0) {
                template.append('&');
            }
            template.append("%61").append(i).append("=template");
        }
        FakeClientRequest request = new FakeClientRequest(ClientUri.create(BASE_URI));
        request.uri(template.toString())
                .pathParam("host", "other.example")
                .pathParam("id", "one")
                .skipUriEncoding(true);
        for (int i = 0; i < queryParamCount; i++) {
            request.queryParam("a" + i, "request" + i);
        }
        return request
                .resolvedUri()
                .pathWithQueryAndFragment();
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
