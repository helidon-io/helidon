/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.ReadOnlyParameters;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MediaSupport;
import io.helidon.webserver.BareRequest;
import io.helidon.webserver.BareResponse;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;

/**
 * Client API designed to create request directly on {@link Routing} without a network layer.
 * It can be used together with {@link RouteMock} to provide some specific behaviors.
 */
public class TestClient {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final Routing routing;
    private final MediaSupport mediaSupport;
    private final Handler mockingHandler;

    /**
     * Create new instance.
     *
     * @param routing a routing to create client
     * @param mockingHandler a handler representing mocking
     * @throws NullPointerException if routing parameter is null
     */
    private TestClient(Routing routing, MediaSupport mediaSupport, Handler mockingHandler) {
        Objects.requireNonNull(routing, "Parameter 'routing' is null!");
        this.routing = routing;
        this.mediaSupport = mediaSupport;
        this.mockingHandler = mockingHandler;
    }

    /**
     * Creates new {@link TestClient} instance with specified routing.
     *
     * @param routingBuilder a routing builder to test; will be built as a first step of this
     *                       method execution
     * @return new instance
     * @throws NullPointerException if routing parameter is null
     */
    public static TestClient create(Supplier<Routing> routingBuilder) {
        Objects.requireNonNull(routingBuilder, "Parameter 'routingBuilder' must not be null!");
        return create(routingBuilder.get());
    }

    /**
     * Creates new {@link TestClient} instance with specified routing.
     *
     * @param routing a routing to test
     * @param mediaSupport media support
     * @return new instance
     * @throws NullPointerException if routing parameter is null
     */
    public static TestClient create(Routing routing, MediaSupport mediaSupport) {
        return new TestClient(routing, mediaSupport, null);
    }

    /**
     * Creates new {@link TestClient} instance with specified routing.
     *
     * @param routing a routing to test
     * @return new instance
     * @throws NullPointerException if routing parameter is null
     */
    public static TestClient create(Routing routing) {
        return new TestClient(routing, null, null);
    }

    /**
     * Creates a request of provided URI path.
     *
     * @param path a path to request
     * @return new test request builder
     */
    public TestRequest path(String path) {
        return new TestRequest(this, path);
    }

    /**
     * Returns response as soon as it has composed headers.
     *
     * @param method an HTTP method
     * @param version an HTTP version
     * @param path a URI path
     * @param headers HTTP headers
     * @param publisher a request body publisher
     * @return new response instance as soon as headers are composed
     * @throws InterruptedException if thread is interrupted
     * @throws RuntimeException if response is not composed but throws exception
     * @throws TimeoutException if request timeouts
     */
    TestResponse call(Http.RequestMethod method,
                      Http.Version version,
                      URI path,
                      Map<String, List<String>> headers,
                      Flow.Publisher<DataChunk> publisher) throws InterruptedException, TimeoutException {
        TestBareRequest req = new TestBareRequest(method, version, path, headers, publisher, mediaSupport);
        TestBareResponse res = new TestBareResponse(req.webServer);
        routing.route(req, res);
        try {
            return res.responseFuture.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof RuntimeException) {
                throw (RuntimeException) ee.getCause();
            } else {
                throw new RuntimeException("Unexpected routing issue.", ee.getCause());
            }
        }
    }

    private static class TestBareRequest implements BareRequest {

        private final Http.RequestMethod method;
        private final Http.Version version;
        private final URI path;
        private final Map<String, List<String>> headers;
        private final Flow.Publisher<DataChunk> publisher;
        private final TestWebServer webServer;

        TestBareRequest(Http.RequestMethod method,
                        Http.Version version,
                        URI path,
                        Map<String, List<String>> headers,
                        Flow.Publisher<DataChunk> publisher,
                        MediaSupport mediaSupport) {
            Objects.requireNonNull(method, "Parameter 'method' is null!");
            Objects.requireNonNull(version, "Parameter 'version' is null!");
            Objects.requireNonNull(path, "Parameter 'path' is null!");
            webServer = new TestWebServer(mediaSupport);
            this.method = method;
            this.version = version;
            this.path = path;
            this.headers = new ReadOnlyParameters(headers).toMap();
            if (publisher == null) {
                this.publisher = Single.<DataChunk>empty();
            } else {
                this.publisher = publisher;
            }
        }

        @Override
        public TestWebServer webServer() {
            return webServer;
        }

        @Override
        public Http.RequestMethod method() {
            return method;
        }

        @Override
        public Http.Version version() {
            return version;
        }

        @Override
        public URI uri() {
            return path;
        }

        @Override
        public String localAddress() {
            return "0.0.0.0";
        }

        @Override
        public int localPort() {
            return 9999;
        }

        @Override
        public String remoteAddress() {
            return "127.0.0.1";
        }

        @Override
        public int remotePort() {
            return 3333;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public Map<String, List<String>> headers() {
            return headers;
        }

        @Override
        public Flow.Publisher<DataChunk> bodyPublisher() {
            return publisher;
        }

        @Override
        public long requestId() {
            return 0;
        }
    }

    static class TestBareResponse implements BareResponse {

        private final CompletableFuture<TestResponse> responseFuture = new CompletableFuture<>();
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        private final CompletableFuture<BareResponse> headersCompletionStage = new CompletableFuture<>();
        private final CompletableFuture<BareResponse> completionStage = new CompletableFuture<>();
        private final TestWebServer webServer;

        private volatile Flow.Subscription subscription;

        TestBareResponse(TestWebServer webServer) {
            this.webServer = webServer;
        }

        TestWebServer webServer() {
            return webServer;
        }

        byte[] asBytes() {
            synchronized (baos) {
                return baos.toByteArray();
            }
        }

        @Override
        public void writeStatusAndHeaders(Http.ResponseStatus status, Map<String, List<String>> headers) {
            headersCompletionStage.complete(this);
            responseFuture.complete(new TestResponse(status, headers, this));
        }

        @Override
        public CompletionStage<BareResponse> whenHeadersCompleted() {
            return headersCompletionStage;
        }

        @Override
        public CompletionStage<BareResponse> whenCompleted() {
            return completionStage;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(DataChunk data) {
            if (data == null) {
                return;
            }
            ByteBuffer bb = data.data();
            try {
                synchronized (baos) {
                    byte[] buff = new byte[bb.remaining()];
                    bb.get(buff);
                    baos.write(buff);
                }
            } catch (IOException e) {
                onError(new IllegalStateException("Cannot write data into the ByteArrayOutputStream!", e));
            }
        }

        @Override
        public void onError(Throwable thr) {
            try {
                subscription.cancel();
            } finally {
                headersCompletionStage.completeExceptionally(thr);
                completionStage.completeExceptionally(thr);
            }
        }

        @Override
        public void onComplete() {
            try {
                subscription.cancel();
            } finally {
                headersCompletionStage.complete(this);
                completionStage.complete(this);
            }
        }

        @Override
        public long requestId() {
            return 0;
        }
    }
}
