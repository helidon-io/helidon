/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.examples.google;

import java.util.Optional;

import io.helidon.common.http.MediaType;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;
import io.helidon.security.google.GoogleTokenProvider;
import io.helidon.security.webserver.WebSecurity;
import io.helidon.webserver.Routing;
import io.helidon.webserver.StaticContentSupport;
import io.helidon.webserver.WebServer;

/**
 * Google login button example main class using builders.
 */
public final class GoogleBuilderMain {
    private static volatile WebServer theServer;

    private GoogleBuilderMain() {
    }

    public static WebServer getTheServer() {
        return theServer;
    }

    /**
     * Start the example.
     *
     * @param args ignored
     * @throws InterruptedException if server startup is interrupted.
     */
    public static void main(String[] args) throws InterruptedException {
        Security security = Security.builder()
                .addProvider(GoogleTokenProvider.builder()
                                     .clientId("your-client-id.apps.googleusercontent.com"))
                .build();
        WebSecurity ws = WebSecurity.from(security);

        Routing.Builder routing = Routing.builder()
                .register(ws)
                .get("/rest/profile",
                     WebSecurity.authenticate(),
                     (req, res) -> {
                         Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);
                         res.headers().contentType(MediaType.TEXT_PLAIN.withCharset("UTF-8"));
                         res.send("Response from builder based service, you are: \n" + securityContext
                                 .flatMap(SecurityContext::getUser)
                                 .map(Subject::toString)
                                 .orElse("Security context is null"));
                         req.next();
                     })
                .register(StaticContentSupport.create("/WEB"));

        theServer = GoogleUtil.startIt(routing);
    }
}
