/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.rest.client.example.webserver;

import javax.json.Json;
import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.media.jsonp.common.JsonProcessing;
import io.helidon.webclient.Proxy;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientException;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webclient.metrics.WebClientMetrics;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webclient.tracing.WebClientTracing;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.WebServer;

/**
 * Webserver with webclient example.
 */
public class WebserverClientExample {
    private static WebClient webClient;

    private WebserverClientExample() {
    }

    /**
     * Creates simple webserver which uses webclient.
     *
     * @param args args
     */
    public static void main(String[] args) {
        Config config = Config.create();

        webClient = WebClient.builder()
                // default configuration of client metrics
                // REQUIRES: metrics registry configured on request context (injected by MetricsSupport)
                .register(WebClientMetrics.create(config.get("client.services.config.metrics")))
                .register(WebClientMetrics.timer()
                                  .methods("GET", "POST")
                                  .nameFormat("neco")
                                  .description("Cool description")
                                  .build())
                // default configuration of tracing
                // REQUIRES: span context configured on request context (injected by future TracingSupport)
                .register(WebClientTracing.create())
                // default configuration of client security - invokes outbound provider(s) and updates headers
                // REQUIRES: security and security context configured on request context (injected by WebSecurity)
                .register(WebClientSecurity.create())
                .proxy(Proxy.create(config))
                .build();

        WebServer server = WebServer.create(Routing.builder()
                                                    .get("/hello", WebserverClientExample::hello)
                                                    .post("/put", WebserverClientExample::put)
                                                    .build());

        server.start()
                .thenAccept(webServer -> System.out.println("Webserver started on http://localhost:" + server.port()));
    }

    private static void put(ServerRequest req, ServerResponse res) {
        JsonObject object = Json.createObjectBuilder()
                .add("key", "value")
                .add("anotherKey", 42)
                .build();

        webClient.put()
                .uri("http://localhost:8080/greeint")
                // request specific handler
                .register(JsonProcessing.create().newWriter())
                .submit(object)
                .thenApply(WebClientResponse::content)
                .thenAccept(res::send)
                .exceptionally(throwable -> handleException(res, throwable));
    }

    private static void proxyResponse(ServerRequest req, ServerResponse res) {
        webClient.get()
                .uri("http://localhost:8080/greet")
                .request()
                .thenAccept(clientResponse -> {
                    res.headers().add("CUSTOM_RESPONSE", "HEADER");
                    res.status(clientResponse.status());
                    res.send(clientResponse.content());
                });
    }

    private static void proxyRequestAndResponse(ServerRequest req, ServerResponse res) {
        webClient.get()
                .uri("http://localhost:8080")
                .path(req.path())
                .queryParams(req.queryParams())
                .headers(req.headers())
                .submit(req.content())
                .thenAccept(clientResponse -> {
                    res.headers().add("CUSTOM_RESPONSE", "HEADER");
                    res.status(clientResponse.status());
                    res.send(clientResponse.content());
                })
                .exceptionally(throwable -> handleException(res, throwable));

    }

    private static Void handleException(ServerResponse res, Throwable throwable) {
        if (throwable instanceof WebClientException) {
            WebClientException e = (WebClientException) throwable;
            e.response()
                    .ifPresentOrElse(clientResponse -> {
                        res.status(clientResponse.status());
                        res.send(clientResponse.content());
                    }, () -> {
                        res.status(Http.Status.INTERNAL_SERVER_ERROR_500);
                        res.send();
                    });

        } else {
            // log and send entity with stacktrace if in debug mode
            res.status(Http.Status.INTERNAL_SERVER_ERROR_500);
            res.send();
        }

        return null;
    }

    private static void hello(ServerRequest req, ServerResponse res) {
        webClient.get()
                .uri("http://www.google.com")
                .request()
                .thenApply(WebClientResponse::content)
                .thenAccept(res::send)
                .exceptionally(throwable -> handleException(res, throwable));
    }

}
