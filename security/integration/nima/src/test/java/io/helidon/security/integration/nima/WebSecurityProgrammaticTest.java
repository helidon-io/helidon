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

package io.helidon.security.integration.nima;

import java.util.Optional;
import java.util.regex.Pattern;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.config.Config;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig;
import io.helidon.nima.webserver.context.ContextFeature;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.util.TokenHandler;

/**
 * Unit test for {@link SecurityFeature}.
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

        Config config = Config.create();

        Security security = Security.builder(config.get("security"))
                .addAuditProvider(myAuditProvider).build();

        Context context = Context.create();
        context.register(myAuditProvider);

        SecurityFeature securityFeature = SecurityFeature.create(security)
                .securityDefaults(SecurityHandler.create()
                        .queryParam("jwt", TokenHandler.builder()
                                .tokenHeader("BEARER_TOKEN")
                                .tokenPattern(Pattern.compile("bearer (.*)"))
                                .build())
                        .queryParam("name", TokenHandler.builder()
                                .tokenHeader("NAME_FROM_REQUEST")
                                .build()));

        serverBuilder.serverContext(context)
                .routing(routing -> routing
                        .addFeature(ContextFeature.create())
                        .addFeature(securityFeature)
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
                        }));
    }
}
