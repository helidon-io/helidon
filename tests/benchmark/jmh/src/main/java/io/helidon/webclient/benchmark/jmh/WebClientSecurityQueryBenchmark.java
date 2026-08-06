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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import io.helidon.common.context.Context;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityEnvironment;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webclient.spi.WebClientService;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
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
public class WebClientSecurityQueryBenchmark {
    private static final URI TARGET_URI = URI.create("https://example.test/items");
    private static final ClientRequestHeaders HEADERS = ClientRequestHeaders.create(WritableHeaders.create());
    private static final WebClientService.Chain CHAIN = request -> null;

    private WebClientSecurity service;
    private Context context;
    private SecurityEnvironment environment;

    @Setup
    public void setup() {
        Security security = Security.builder()
                .addOutboundSecurityProvider((request, environment, config) -> {
                    this.environment = environment;
                    return OutboundSecurityResponse.abstain();
                })
                .build();
        context = Context.create();
        context.register(security.contextBuilder("jmh").build());
        service = WebClientSecurity.create();
    }

    @Benchmark
    public String defaultEncoding() {
        return applySecurity(false, false);
    }

    @Benchmark
    public String skipEncoding() {
        return applySecurity(true, false);
    }

    @Benchmark
    public String skipEncodingPlain() {
        return applySecurity(true, true);
    }

    private String applySecurity(boolean skipEncoding, boolean plainValues) {
        ClientUri uri = ClientUri.create(TARGET_URI);
        uri.skipUriEncoding(skipEncoding);
        if (plainValues) {
            uri.writeableQuery().set("one", "valueOne");
            uri.writeableQuery().set("two", "valueTwo");
            uri.writeableQuery().set("three", "valueThree");
            uri.writeableQuery().set("four", "valueFour");
        } else {
            uri.writeableQuery().set("one", "value%2Fone");
            uri.writeableQuery().set("two", "value#two");
            uri.writeableQuery().set("three", "value three");
            uri.writeableQuery().set("four", "value+four");
        }
        environment = null;
        service.handle(CHAIN, new BenchmarkServiceRequest(uri, context));
        return environment.requestedQuery().orElseThrow().rawValue();
    }

    private static final class BenchmarkServiceRequest implements WebClientServiceRequest {
        private final ClientUri uri;
        private final Context context;
        private String requestId = "jmh";

        private BenchmarkServiceRequest(ClientUri uri, Context context) {
            this.uri = uri;
            this.context = context;
        }

        @Override
        public ClientUri uri() {
            return uri;
        }

        @Override
        public Method method() {
            return Method.GET;
        }

        @Override
        public String protocolId() {
            return "http/1.1";
        }

        @Override
        public ClientRequestHeaders headers() {
            return HEADERS;
        }

        @Override
        public Context context() {
            return context;
        }

        @Override
        public String requestId() {
            return requestId;
        }

        @Override
        public void requestId(String requestId) {
            this.requestId = requestId;
        }

        @Override
        public CompletionStage<WebClientServiceRequest> whenSent() {
            return CompletableFuture.completedStage(this);
        }

        @Override
        public CompletionStage<WebClientServiceResponse> whenComplete() {
            return CompletableFuture.completedStage(null);
        }

        @Override
        public Map<String, String> properties() {
            return Map.of();
        }
    }
}
