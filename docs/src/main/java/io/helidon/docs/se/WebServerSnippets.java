/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.se;

import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;

// tag::snippet_13[]
import io.helidon.common.configurable.AllowList;
// end::snippet_13[]
// tag::snippet_16[]
import io.helidon.common.tls.Tls;
import io.helidon.common.uri.UriInfo;
// end::snippet_16[]
import io.helidon.config.Config;
import io.helidon.http.HttpException;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.accesslog.AccessLogFeature;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.staticcontent.StaticContentService;

// tag::snippet_14[]
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

import static io.helidon.http.RequestedUriDiscoveryContext.RequestedUriDiscoveryType.FORWARDED;
import static io.helidon.http.RequestedUriDiscoveryContext.RequestedUriDiscoveryType.X_FORWARDED;
// end::snippet_14[]

@SuppressWarnings("ALL")
class WebServerSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        WebServer.builder()
                .port(8080)
                .build()
                .start();
        // end::snippet_1[]
    }

    void snippet_2() {
        // tag::snippet_2[]
        Config config = Config.create(); // <1>
        WebServer.builder()
                .config(config.get("server")); // <2>
        // end::snippet_2[]
    }

    void snippet_3() {
        // tag::snippet_3[]
        WebServer.builder()
                .routing(it -> it
                        .get("/hello", (req, res) -> res.send("Hello World!"))) // <1>
                .build(); // <2>
        // end::snippet_3[]
    }

    void snippet_4(HttpRouting.Builder routing) {
        // tag::snippet_4[]
        routing.post("/some/path", (req, res) -> { /* handler */ });
        // end::snippet_4[]
    }

    void snippet_5(HttpRouting.Builder routing) {
        // tag::snippet_5[]
        routing.route(HttpRoute.builder()
                              .path("/hello")
                              .methods(Method.POST, Method.PUT) // <1>
                              .handler((req, res) -> {
                                  String requestEntity = req.content().as(String.class);
                                  res.send(requestEntity); // <2>
                              }));
        // end::snippet_5[]
    }

    void snippet_6(HttpRouting.Builder routing) {
        // tag::snippet_6[]
        routing.register("/hello", new HelloService());
        // end::snippet_6[]
    }

    // tag::snippet_7[]
    class HelloService implements HttpService {
        @Override
        public void routing(HttpRules rules) {
            rules.get("/subpath", (req, res) -> {
                // Some logic
            });
        }
    }
    // end::snippet_7[]

    void snippet_8(HttpRouting.Builder routing) {
        // tag::snippet_8[]
        routing.addFilter((chain, req, res) -> {
            try {
                chain.proceed();
            } finally {
                // do something for any finished request
            }
        });
        // end::snippet_8[]
    }

    void snippet_9(HttpRules rules) {
        // tag::snippet_9[]
        rules.any("/hello", (req, res) -> { // <1>
            // filtering logic  // <2>
            res.next(); // <3>
        });
        // end::snippet_9[]
    }

    // stub
    boolean userParametersOk() {
        return true;
    }

    void snippet_10(HttpRules rules) {
        // tag::snippet_10[]
        rules.any("/hello", (req, res) -> { // <1>
            // filtering logic (e.g., validating parameters) // <2>
            if (userParametersOk()) {
                res.next(); // <3>
            } else {
                throw new IllegalArgumentException("Invalid parameters."); // <4>
            }
        });
        // end::snippet_10[]
    }

    void snippet_11(HttpRules rules) {
        // tag::snippet_11[]
        rules.get("/hello", (req, res) -> { // <1>
            // terminating logic
            res.status(Status.ACCEPTED_202)
                    .send("Saved!"); // <2>
        });
        // end::snippet_11[]
    }

    void snippet_12(HttpRules rules) {
        // tag::snippet_12[]
        rules.get("/any-version", (req, res) -> res.send("HTTP Version " + req.prologue().protocolVersion())) // <1>
                .route(Http1Route.route(Method.GET, "/version-specific", (req, res) -> res.send("HTTP/1.1 route"))) // <2>
                .route(Http2Route.route(Method.GET, "/version-specific", (req, res) -> res.send("HTTP/2 route"))); // <3>
        // end::snippet_12[]
    }

    void snippet_15() {
        // tag::snippet_15[]
        AllowList trustedProxies = AllowList.builder()
                .addAllowedPattern(Pattern.compile("lb.+\\.mycorp\\.com"))
                .addDenied("lbtest.mycorp.com")
                .build(); // <1>

        WebServer.builder()
                .requestedUriDiscoveryContext(it -> it
                        .addDiscoveryType(FORWARDED) // <2>
                        .addDiscoveryType(X_FORWARDED)
                        .trustedProxies(trustedProxies)); // <3>
        // end::snippet_15[]
    }

    void snippet_17(HttpRules rules) {
        // tag::snippet_17[]
        rules.get((req, res) -> {
            UriInfo uriInfo = req.requestedUri();
        });
        // end::snippet_17[]
    }

    // stub
    static final class MyException extends RuntimeException {
    }

    void snippet_18(HttpRouting.Builder routing) {
        Object errorDescriptionObject = new Object();

        // tag::snippet_18[]
        routing.error(MyException.class, (req, res, ex) -> { // <1>
            // handle the error, set the HTTP status code
            res.send(errorDescriptionObject); // <2>
        });
        // end::snippet_18[]
    }

    void snippet_19(HttpRouting.Builder routing) {
        // tag::snippet_19[]
        routing.error(MyException.class, (req, res, ex) -> {
            res.status(Status.BAD_REQUEST_400);
            res.send("Unable to parse request. Message: " + ex.getMessage());
        });
        // end::snippet_19[]
    }

    void snippet_20(HttpRouting.Builder routing) {
        // tag::snippet_20[]
        routing.error(MyException.class, (req, res, ex) -> {
            // some logic
            throw ex;
        });
        // end::snippet_20[]
    }

    void snippet_21(HttpRules rules) {
        // tag::snippet_21[]
        rules.get((req, res) -> {
            throw new HttpException(
                    "Amount of money must be greater than 0.",
                    Status.NOT_ACCEPTABLE_406); // <1>
        });
        // end::snippet_21[]
    }

    void snippet_22(HttpRouting.Builder routing) {
        // tag::snippet_22[]
        routing.register("/pictures", StaticContentService.create(Paths.get("/some/WEB/pics"))) // <1>
                .register("/", StaticContentService.builder("/static-content") // <2>
                        .welcomeFileName("index.html") // <3>
                        .build());
        // end::snippet_22[]
    }

    void snippet_23() {
        // tag::snippet_23[]
        WebServer.builder()
                .mediaContext(it -> it
                        .mediaSupportsDiscoverServices(false)
                        .addMediaSupport(JsonpSupport.create())
                        .build());
        // end::snippet_23[]
    }

    // tag::snippet_24[]
    static final JsonBuilderFactory JSON_FACTORY = Json.createBuilderFactory(Map.of()); // <1>
    // end::snippet_24[]

    void snippet_25(HttpRules rules) {
        // tag::snippet_25[]
        rules.post("/hello", (req, res) -> {
            JsonObject requestEntity = req.content().as(JsonObject.class); // <2>
            JsonObject responseEntity = JSON_FACTORY.createObjectBuilder() // <3>
                    .add("message", "Hello " + requestEntity.getString("name"))
                    .build();
            res.send(responseEntity); // <4>
        });
        // end::snippet_25[]
    }

    // tag::snippet_26[]
    public class Person {

        private String name;

        public Person() {
            super();
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
    // end::snippet_26[]

    void snippet_27(HttpRules rules) {
        // tag::snippet_27[]
        rules.post("/echo", (req, res) -> {
            res.send(req.content().as(Person.class)); // <1>
        });
        // end::snippet_27[]
    }

    void snippet_28(HttpRules rules) {
        // tag::snippet_28[]
        rules.post("/echo", (req, res) -> {
            res.send(req.content().as(Person.class)); // <1>
        });
        // end::snippet_28[]
    }

    void snippet_29() {
        // tag::snippet_29[]
        WebServer.builder()
                .addFeature(AccessLogFeature.builder()
                                    .commonLogFormat()
                                    .build());
        // end::snippet_29[]
    }

    void snippet_30() {
        // tag::snippet_30[]
        Tls tls = Tls.builder()
                .privateKey(pk -> pk
                        .keystore(keys -> keys.keystore(it -> it.resourcePath("private-key.p12"))
                                .passphrase("password".toCharArray())))
                .trust(trust -> trust
                        .keystore(keys -> keys.keystore(it -> it.resourcePath("trust.p12"))))
                .build();

        WebServer.builder()
                .tls(tls);
        // end::snippet_30[]
    }

    void snippet_31() {
        // tag::snippet_31[]
        Config config = Config.create();
        WebServer.builder()
                .tls(it -> it.config(config.get("server.tls")));
        // end::snippet_31[]
    }

    void snippet_32() {
        // tag::snippet_32[]
        WebServer.builder()
                .contentEncoding(it -> it
                .contentEncodingsDiscoverServices(false)
                .addContentEncoding(GzipEncoding.create()));
        // end::snippet_32[]
    }

    void snippet_33() {
        // tag::snippet_33[]
        WebServer.builder()
                .enableProxyProtocol(true);
        // end::snippet_33[]
    }

    void snippet_34(HttpRules rules) {
        // tag::snippet_34[]
        rules.get("/", (req, res) -> {
            ProxyProtocolData data = req.proxyProtocolData().orElse(null);
            if (data != null
                && data.family() == ProxyProtocolData.Family.IPv4
                && data.protocol() == ProxyProtocolData.Protocol.TCP
                && data.sourceAddress().equals("192.168.0.1")
                && data.destAddress().equals("192.168.0.11")
                && data.sourcePort() == 56324
                && data.destPort() == 443) {
                // ...
            }
        });
        // end::snippet_34[]
    }

    class Snippet35 {
        // tag::snippet_35[]
        public static void main(String[] args) {
            WebServer webServer = WebServer.builder()
                    .routing(it -> it.any((req, res) -> res.send("It works!"))) // <1>
                    .build() // <2>
                    .start(); // <3>

            System.out.println("Server started at: http://localhost:" + webServer.port()); // <4>
        }
        // end::snippet_35[]
    }
}
