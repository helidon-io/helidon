/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.security.integration.nima;

import java.util.Optional;
import java.util.regex.Pattern;

import io.helidon.common.context.Contexts;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.config.Config;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.context.ContextFeature;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.util.TokenHandler;

import org.junit.jupiter.api.BeforeAll;

/**
 * Unit test for {@link SecurityFeature}.
 */
public class WebSecurityProgrammaticTest extends WebSecurityTests {
    private static String baseUri;

    @BeforeAll
    public static void initClass() {
        WebSecurityTestUtil.auditLogFinest();
        myAuditProvider = new UnitTestAuditProvider();

        Config config = Config.create();

        Security security = Security.builder(config.get("security"))
                .addAuditProvider(myAuditProvider).build();

        server = WebServer.builder()
                .routing(routing -> routing.addFeature(ContextFeature.create())
                         .addFeature(SecurityFeature.create(security)
                                                             .securityDefaults(
                                                                     SecurityHandler.create()
                                                                             .queryParam(
                                                                                     "jwt",
                                                                                     TokenHandler.builder()
                                                                                             .tokenHeader("BEARER_TOKEN")
                                                                                             .tokenPattern(Pattern.compile(
                                                                                                     "bearer (.*)"))
                                                                                             .build())
                                                                             .queryParam(
                                                                                     "name",
                                                                                     TokenHandler.builder()
                                                                                             .tokenHeader("NAME_FROM_REQUEST")
                                                                                             .build())))
                        .get("/noRoles", SecurityFeature.secure())
                        .get("/user[/{*}]", SecurityFeature.rolesAllowed("user"))
                        .get("/admin", SecurityFeature.rolesAllowed("admin"))
                        .get("/deny", SecurityFeature.rolesAllowed("deny"), (req, res) -> {
                            res.status(Http.Status.INTERNAL_SERVER_ERROR_500);
                            res.send("Should not get here, this role doesn't exist");
                        })
                        .get("/auditOnly", SecurityFeature
                                .audit()
                                .auditEventType("unit_test")
                                .auditMessageFormat(AUDIT_MESSAGE_FORMAT)
                        )
                        .get("/{*}", (req, res) -> {
                            Optional<SecurityContext> securityContext = Contexts.context()
                                    .flatMap(it -> it.get(SecurityContext.class));
                            res.headers().contentType(HttpMediaType.PLAINTEXT_UTF_8);
                            res.send("Hello, you are: \n" + securityContext
                                    .map(ctx -> ctx.user().orElse(SecurityContext.ANONYMOUS).toString())
                                    .orElse("Security context is null"));
                        }))
                .build();

        server.start();
        baseUri = "http://localhost:" + server.port();
    }

    @Override
    String serverBaseUri() {
        return baseUri;
    }
}
