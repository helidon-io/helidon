/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.security.google;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.helidon.http.HttpMediaTypes;
import io.helidon.logging.common.LogConfig;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;
import io.helidon.security.providers.google.login.GoogleTokenProvider;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.security.SecurityFeature;
import io.helidon.webserver.staticcontent.StaticContentService;

/**
 * Google login button example main class using builders.
 */
@SuppressWarnings({"SpellCheckingInspection", "DuplicatedCode"})
public final class GoogleBuilderMain {

    private GoogleBuilderMain() {
    }

    /**
     * Start the example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        LogConfig.configureRuntime();
        WebServerConfig.Builder builder = WebServerConfig.builder();
        setup(builder);
        WebServer server = builder.build();

        long t = System.nanoTime();
        server.start();
        long time = System.nanoTime() - t;

        System.out.printf("""
                        Server started in %d ms
                        Started server on localhost: %2$d
                        You can access this example at http://localhost:%2$d/index.html

                        Check application.yaml in case you are behind a proxy to configure it
                        """,
                TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS),
                server.port());
    }

    static void setup(WebServerConfig.Builder server) {
        Security security = Security.builder()
                .addProvider(GoogleTokenProvider.builder()
                        .clientId("your-client-id.apps.googleusercontent.com"))
                .build();
        server.featuresDiscoverServices(false)
                .addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.builder()
                                    .security(security)
                                    .build())
                .routing(routing -> routing
                .get("/rest/profile", SecurityFeature.authenticate(),
                     (req, res) -> {
                            Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);
                            res.headers().contentType(HttpMediaTypes.PLAINTEXT_UTF_8);
                            res.send("Response from builder based service, you are: \n" + securityContext
                                    .flatMap(SecurityContext::user)
                                    .map(Subject::toString)
                                    .orElse("Security context is null"));
                            res.next();
                        })
                .register(StaticContentService.create("/WEB")));
    }
}
