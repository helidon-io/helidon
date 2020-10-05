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

package io.helidon.security.examples.webserver.basic;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.LogConfig;
import io.helidon.common.http.MediaType;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.providers.httpauth.SecureUserStore;
import io.helidon.webserver.Routing;
import io.helidon.webserver.StaticContentSupport;
import io.helidon.webserver.WebServer;

public class BasicExampleBuilderMain {
    // simple approach to user storage - for real world, use data store...
    private static final Map<String, MyUser> USERS = new HashMap<>();

    static {
        USERS.put("jack", new MyUser("jack", "password".toCharArray(), Set.of("user", "admin")));
        USERS.put("jill", new MyUser("jill", "password".toCharArray(), Set.of("user")));
        USERS.put("john", new MyUser("john", "password".toCharArray(), Set.of()));
    }

    public static void main(String[] args) {
        BasicExampleUtil.startAndPrintEndpoints(BasicExampleBuilderMain::startServer);
    }

    static WebServer startServer() {
        LogConfig.initClass();

        Routing routing = Routing.builder()
                // must be configured first, to protect endpoints
                .register(buildWebSecurity().securityDefaults(WebSecurity.authenticate()))
                .any("/static[/{*}]", WebSecurity.rolesAllowed("user"))
                .register("/static", StaticContentSupport.create("/WEB"))
                .get("/noRoles", WebSecurity.enforce())
                .get("/user[/{*}]", WebSecurity.rolesAllowed("user"))
                .get("/admin", WebSecurity.rolesAllowed("admin"))
                // audit is not enabled for GET methods by default
                .get("/deny", WebSecurity.rolesAllowed("deny").audit())
                // roles allowed imply authn and authz
                .any("/noAuthn", WebSecurity.rolesAllowed("admin")
                        .authenticationOptional()
                        .audit())
                .get("/{*}", (req, res) -> {
                    Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);
                    res.headers().contentType(MediaType.TEXT_PLAIN.withCharset("UTF-8"));
                    res.send("Hello, you are: \n" + securityContext
                            .map(ctx -> ctx.user().orElse(SecurityContext.ANONYMOUS).toString())
                            .orElse("Security context is null"));
                })
                .build();

        return WebServer.builder()
                .routing(routing)
                // uncomment to use an explicit port
                //.port(8080)
                .build()
                .start()
                .await(10, TimeUnit.SECONDS);

    }

    private static WebSecurity buildWebSecurity() {
        Security security = Security.builder()
                .addAuthenticationProvider(
                        HttpBasicAuthProvider.builder()
                                .realm("helidon")
                                .userStore(buildUserStore()),
                        "http-basic-auth")
                .build();
        return WebSecurity.create(security);
    }

    private static SecureUserStore buildUserStore() {
        return login -> Optional.ofNullable(USERS.get(login));
    }

    private static class MyUser implements SecureUserStore.User {
        private final String login;
        private final char[] password;
        private final Set<String> roles;

        private MyUser(String login, char[] password, Set<String> roles) {
            this.login = login;
            this.password = password;
            this.roles = roles;
        }

        private char[] password() {
            return password;
        }

        @Override
        public boolean isPasswordValid(char[] password) {
            return Arrays.equals(password(), password);
        }

        @Override
        public Set<String> roles() {
            return roles;
        }

        @Override
        public String login() {
            return login;
        }
    }
}
