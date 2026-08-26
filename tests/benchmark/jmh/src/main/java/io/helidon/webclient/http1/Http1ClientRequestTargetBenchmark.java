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

package io.helidon.webclient.http1;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.context.Context;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class Http1ClientRequestTargetBenchmark {
    @Param
    private Fragment fragment;

    private BufferData buffer;
    private Http1CallChainBase chain;
    private ClientUri uri;
    private WebClientServiceRequest request;

    @Setup
    public void setup() {
        Http1ClientImpl client = (Http1ClientImpl) Http1Client.create();
        Http1ClientRequestImpl originalRequest = (Http1ClientRequestImpl) client.get("http://localhost/test");
        chain = new BenchmarkChain(client, originalRequest);

        String fragmentPart = fragment == Fragment.PRESENT ? "#fragment" : "";
        uri = ClientUri.create(URI.create("http://localhost/test?query=value" + fragmentPart));
        request = new BenchmarkRequest(uri);
        buffer = BufferData.growing(256);
    }

    @Benchmark
    public BufferData prologue() {
        buffer.clear();
        chain.prologue(null, buffer, request, uri);
        return buffer;
    }

    public enum Fragment {
        ABSENT,
        PRESENT
    }

    private static final class BenchmarkChain extends Http1CallChainBase {
        private BenchmarkChain(Http1ClientImpl client, Http1ClientRequestImpl request) {
            super(client, request, new CompletableFuture<>());
        }

        @Override
        WebClientServiceResponse doProceed(ClientConnection connection,
                                           WebClientServiceRequest request,
                                           ClientRequestHeaders headers,
                                           DataWriter writer,
                                           DataReader reader,
                                           BufferData writeBuffer) {
            return null;
        }
    }

    private static final class BenchmarkRequest implements WebClientServiceRequest {
        private final ClientUri uri;
        private final ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        private final Context context = Context.create();
        private final CompletionStage<WebClientServiceRequest> whenSent = CompletableFuture.completedFuture(this);
        private final CompletionStage<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        private final Map<String, String> properties = new HashMap<>();

        private String requestId = "benchmark";

        private BenchmarkRequest(ClientUri uri) {
            this.uri = uri;
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
            return Http1Client.PROTOCOL_ID;
        }

        @Override
        public ClientRequestHeaders headers() {
            return headers;
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
            return whenSent;
        }

        @Override
        public CompletionStage<WebClientServiceResponse> whenComplete() {
            return whenComplete;
        }

        @Override
        public Map<String, String> properties() {
            return properties;
        }
    }
}
