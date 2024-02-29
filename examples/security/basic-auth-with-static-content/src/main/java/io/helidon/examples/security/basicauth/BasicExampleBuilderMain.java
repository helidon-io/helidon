/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.security.basicauth;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.http.HttpMediaTypes;
import io.helidon.logging.common.LogConfig;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.providers.httpauth.SecureUserStore;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.security.SecurityFeature;
import io.helidon.webserver.staticcontent.StaticContentService;

/**
 * Example using {@link io.helidon.common.Builder} approach instead of configuration based approach.
 */
public final class BasicExampleBuilderMain {
    // simple approach to user storage - for real world, use data store...
    private static final Map<String, MyUser> USERS = new HashMap<>();

    static {
        USERS.put("jack", new MyUser("jack", "changeit".toCharArray(), Set.of("user", "admin")));
        USERS.put("jill", new MyUser("jill", "changeit".toCharArray(), Set.of("user")));
        USERS.put("john", new MyUser("john", "changeit".toCharArray(), Set.of()));
    }

    private BasicExampleBuilderMain() {
    }

    /**
     * Entry point, starts the server.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        LogConfig.initClass();

        WebServerConfig.Builder builder = WebServer.builder()
                .port(8080);
        setup(builder);
        WebServer server = builder.build();

        long t = System.nanoTime();
        server.start();
        long time = System.nanoTime() - t;

        System.out.printf("""
                Server started in %d ms

                Signature example: from builder

                "Users:
                jack/password in roles: user, admin
                jill/password in roles: user
                john/password in no roles

                ***********************
                ** Endpoints:        **
                ***********************

                No authentication: http://localhost:8080/public
                No roles required, authenticated: http://localhost:8080/noRoles
                User role required: http://localhost:8080/user
                Admin role required: http://localhost:8080/admin
                Always forbidden (uses role nobody is in), audited: http://localhost:8080/deny
                Admin role required, authenticated, authentication optional, audited \
                (always forbidden - challenge is not returned as authentication is optional): http://localhost:8080/noAuthn
                Static content, requires user role: http://localhost:8080/static/index.html

                """, TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS));
    }

    static void setup(WebServerConfig.Builder server) {
        server.featuresDiscoverServices(false)
                // only add security feature (as we do not use configuration, only features listed here will be available)
                .addFeature(SecurityFeature.builder()
                                    .security(buildSecurity())
                                    .defaults(SecurityFeature.authenticate())
                                    .build())
                .routing(routing -> routing
                // must be configured first, to protect endpoints
                .any("/static[/{*}]", SecurityFeature.rolesAllowed("user"))
                .register("/static", StaticContentService.create("/WEB"))
                .get("/noRoles", SecurityFeature.enforce())
                .get("/user[/{*}]", SecurityFeature.rolesAllowed("user"))
                .get("/admin", SecurityFeature.rolesAllowed("admin"))
                // audit is not enabled for GET methods by default
                .get("/deny", SecurityFeature.rolesAllowed("deny").audit())
                // roles allowed imply authn and authz
                .any("/noAuthn", SecurityFeature.rolesAllowed("admin")
                        .authenticationOptional()
                        .audit())
                .get("/{*}", (req, res) -> {
                    Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);
                    res.headers().contentType(HttpMediaTypes.PLAINTEXT_UTF_8);
                    res.send("Hello, you are: \n" + securityContext
                            .map(ctx -> ctx.user().orElse(SecurityContext.ANONYMOUS).toString())
                            .orElse("Security context is null"));
                }));
    }

    private static Security buildSecurity() {
        return Security.builder()
                .addAuthenticationProvider(
                        HttpBasicAuthProvider.builder()
                                .realm("helidon")
                                .userStore(buildUserStore()),
                        "http-basic-auth")
                .build();
    }

    private static SecureUserStore buildUserStore() {
        return login -> Optional.ofNullable(USERS.get(login));
    }

    private record MyUser(String login, char[] password, Set<String> roles) implements SecureUserStore.User {

        @Override
        public boolean isPasswordValid(char[] password) {
            return Arrays.equals(password(), password);
        }
    }
}
