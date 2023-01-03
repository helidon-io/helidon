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

import io.helidon.common.context.Contexts;
import io.helidon.common.http.HttpMediaType;
import io.helidon.config.Config;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.context.ContextFeature;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;

import org.junit.jupiter.api.BeforeAll;

/**
 * Unit test for {@link SecurityFeature}.
 */
public class WebSecurityFromConfigTest extends WebSecurityTests {
    private static String baseUri;

    @BeforeAll
    public static void initClass() throws InterruptedException {
        WebSecurityTestUtil.auditLogFinest();
        myAuditProvider = new UnitTestAuditProvider();

        Config securityConfig = Config.create().get("security");

        Security security = Security.builder(securityConfig)
                .addAuditProvider(myAuditProvider).build();

        server = WebServer.builder()
                .routing(routing -> routing.addFeature(ContextFeature.create())
                        .addFeature(SecurityFeature.create(security, securityConfig))
                        .get("/*", (req, res) -> {
                            Optional<SecurityContext> securityContext = Contexts.context()
                                    .flatMap(ctx -> ctx.get(SecurityContext.class));
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
