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

package io.helidon.webserver.benchmark.jmh;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.HttpServiceLocator;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class RoutingJmhTest {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final Header CONTENT_TYPE = HeaderValues.createCached(HeaderNames.CONTENT_TYPE,
                                                                         "text/plain; charset=UTF-8");
    private static final Header CONTENT_LENGTH = HeaderValues.createCached(HeaderNames.CONTENT_LENGTH, "2");
    private static final Header SERVER = HeaderValues.createCached(HeaderNames.SERVER, "Helidon");
    private static final byte[] RESPONSE_BYTES = "OK".getBytes(StandardCharsets.UTF_8);

    private WebServer server;
    private int serverPort;
    private HttpClient http1Client;
    private HttpRequest directRouteRequest;
    private HttpRequest staticNestedServiceRequest;
    private HttpRequest staticNestedServiceFallbackRequest;
    private HttpRequest locatorCachedServiceRequest;
    private HttpRequest locatorFallbackRequest;

    @Setup
    public void setup() {
        LogConfig.configureRuntime();

        server = WebServer.builder()
                .connectionOptions(builder -> builder
                        .readTimeout(Duration.ZERO)
                        .connectTimeout(Duration.ZERO)
                        .socketSendBufferSize(64000)
                        .socketReceiveBufferSize(64000))
                .writeQueueLength(4000)
                .host(SERVER_HOST)
                .backlog(8192)
                .routing(router -> router
                        .get("/direct/{id}", new OkHandler())
                        .register("/static", new StaticService())
                        .register("/fallback/{tenant}", new EmptyNestedService())
                        .get("/fallback/{tenant}/items/{id}", new OkHandler())
                        .register("/located/{tenant}", new TenantLocator(Map.of("acme", new TenantService())))
                        .get("/located/{tenant}/items/{id}", new OkHandler()))
                .build()
                .start();

        serverPort = server.port();
        http1Client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        directRouteRequest = request(serverPort, "/direct/42");
        staticNestedServiceRequest = request(serverPort, "/static/acme/items/42");
        staticNestedServiceFallbackRequest = request(serverPort, "/fallback/acme/items/42");
        locatorCachedServiceRequest = request(serverPort, "/located/acme/items/42");
        locatorFallbackRequest = request(serverPort, "/located/unknown/items/42");
    }

    @TearDown
    public void tearDown() {
        server.stop();
    }

    @Benchmark
    public void directRoute(Blackhole bh) throws IOException, InterruptedException {
        bh.consume(send(directRouteRequest));
    }

    @Benchmark
    public void staticNestedService(Blackhole bh) throws IOException, InterruptedException {
        bh.consume(send(staticNestedServiceRequest));
    }

    @Benchmark
    public void staticNestedServiceFallback(Blackhole bh) throws IOException, InterruptedException {
        bh.consume(send(staticNestedServiceFallbackRequest));
    }

    @Benchmark
    public void locatorCachedService(Blackhole bh) throws IOException, InterruptedException {
        bh.consume(send(locatorCachedServiceRequest));
    }

    @Benchmark
    public void locatorFallback(Blackhole bh) throws IOException, InterruptedException {
        bh.consume(send(locatorFallbackRequest));
    }

    private HttpResponse<byte[]> send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = http1Client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Unexpected status: " + response.statusCode());
        }
        return response;
    }

    private static HttpRequest request(int serverPort, String path) {
        return HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://" + SERVER_HOST + ":" + serverPort + path))
                .build();
    }

    private static class StaticService implements HttpService {
        @Override
        public void routing(HttpRules rules) {
            rules.register("/{tenant}", new TenantService());
        }
    }

    private static class TenantService implements HttpService {
        @Override
        public void routing(HttpRules rules) {
            rules.get("/items/{id}", new OkHandler());
        }
    }

    private static class EmptyNestedService implements HttpService {
        @Override
        public void routing(HttpRules rules) {
            rules.get("/other", new OkHandler());
        }
    }

    private static class TenantLocator implements HttpServiceLocator {
        private final Map<String, HttpService> services;

        TenantLocator(Map<String, HttpService> services) {
            this.services = services;
        }

        @Override
        public Optional<HttpService> locate(ServerRequest request) {
            return request.path()
                    .pathParameters()
                    .first("tenant")
                    .map(services::get);
        }
    }

    private static class OkHandler implements Handler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            res.header(CONTENT_LENGTH);
            res.header(CONTENT_TYPE);
            res.header(SERVER);
            res.send(RESPONSE_BYTES);
        }
    }
}
