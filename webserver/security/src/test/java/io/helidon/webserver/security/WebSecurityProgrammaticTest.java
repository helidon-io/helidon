/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.security;

import java.util.Optional;
import java.util.regex.Pattern;

import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HttpMediaTypes;
import io.helidon.http.Status;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.util.TokenHandler;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

/**
 * Unit test for {@link SecurityHttpFeature}.
 */
@ServerTest
public class WebSecurityProgrammaticTest extends WebSecurityTests {

    WebSecurityProgrammaticTest(WebServer server, Http1Client webClient) {
        super(server, webClient);
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder serverBuilder) {
        UnitTestAuditProvider myAuditProvider = new UnitTestAuditProvider();
        WebSecurityTestUtil.auditLogFinest();

        Config config = Config.just(ConfigSources.classpath("security-application.yaml"));

        Security security = Security.builder(config.get("security"))
                .addAuditProvider(myAuditProvider).build();

        Contexts.context()
                .orElseGet(Contexts::globalContext)
                .register(myAuditProvider);

        serverBuilder.featuresDiscoverServices(false)
                .addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.builder()
                                    .security(security)
                                    .defaults(SecurityHandler.create()
                                                      .queryParam("jwt", TokenHandler.builder()
                                                              .tokenHeader("BEARER_TOKEN")
                                                              .tokenPattern(Pattern.compile("bearer (.*)"))
                                                              .build())
                                                      .queryParam("name", TokenHandler.builder()
                                                              .tokenHeader("NAME_FROM_REQUEST")
                                                              .build()))
                                    .build())
                .routing(routing -> routing
                        .get("/noRoles", SecurityFeature.secure())
                        .get("/user/*", SecurityFeature.rolesAllowed("user"))
                        .get("/admin", SecurityFeature.rolesAllowed("admin"))
                        .get("/deny", SecurityFeature.rolesAllowed("deny"), (req, res) -> {
                            res.status(Status.INTERNAL_SERVER_ERROR_500);
                            res.send("Should not get here, this role doesn't exist");
                        })
                        .get("/auditOnly", SecurityFeature
                                .audit()
                                .auditEventType("unit_test")
                                .auditMessageFormat(AUDIT_MESSAGE_FORMAT)
                        )
                        .get("/*", (req, res) -> {
                            Optional<SecurityContext> securityContext = Contexts.context()
                                    .flatMap(it -> it.get(SecurityContext.class));
                            res.headers().contentType(HttpMediaTypes.PLAINTEXT_UTF_8);
                            res.send("Hello, you are: \n" + securityContext
                                    .map(ctx -> ctx.user().orElse(SecurityContext.ANONYMOUS).toString())
                                    .orElse("Security context is null"));
                        }));
    }
}
