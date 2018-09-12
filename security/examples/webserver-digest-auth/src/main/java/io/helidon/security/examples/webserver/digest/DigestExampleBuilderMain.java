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

package io.helidon.security.examples.webserver.digest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.LogManager;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.http.MediaType;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.provider.httpauth.HttpDigestAuthProvider;
import io.helidon.security.provider.httpauth.UserStore;
import io.helidon.security.webserver.WebSecurity;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * Example of HTTP digest authentication with RX Web Server fully configured programmatically.
 */
public final class DigestExampleBuilderMain {
    // used from unit tests
    private static WebServer server;
    // simple approach to user storage - for real world, use data store...
    private static Map<String, MyUser> users = new HashMap<>();

    static {
        users.put("jack", new MyUser("jack", "password".toCharArray(), CollectionsHelper.setOf("user", "admin")));
        users.put("jill", new MyUser("jill", "password".toCharArray(), CollectionsHelper.setOf("user")));
        users.put("john", new MyUser("john", "password".toCharArray(), CollectionsHelper.setOf()));
    }

    private DigestExampleBuilderMain() {
    }

    /**
     * Starts this example. Programmatical configuration. See standard output for instructions.
     *
     * @param args ignored
     * @throws IOException in case of logging configuration failure
     */
    public static void main(String[] args) throws IOException {
        // load logging configuration
        LogManager.getLogManager().readConfiguration(DigestExampleConfigMain.class.getResourceAsStream("/logging.properties"));

        // build routing (same as done in application.conf)
        Routing routing = Routing.builder()
                .register(buildWebSecurity().securityDefaults(WebSecurity.authenticate()))
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
                            .map(ctx -> ctx.getUser().orElse(SecurityContext.ANONYMOUS).toString())
                            .orElse("Security context is null"));
                })
                .build();

        // start server (blocks until started)
        server = DigestExampleUtil.startServer(routing);
    }

    private static WebSecurity buildWebSecurity() {
        Security security = Security.builder()
                .addAuthenticationProvider(
                        HttpDigestAuthProvider.builder()
                                .realm("mic")
                                .digestServerSecret("aPassword".toCharArray())
                                .userStore(buildUserStore()),
                        "digest-auth")
                .build();
        return WebSecurity.from(security);
    }

    private static UserStore buildUserStore() {
        return login -> Optional.ofNullable(users.get(login));
    }

    static WebServer getServer() {
        return server;
    }

    private static class MyUser implements UserStore.User {
        private String login;
        private char[] password;
        private Set<String> roles;

        private MyUser(String login, char[] password, Set<String> roles) {
            this.login = login;
            this.password = password;
            this.roles = roles;
        }

        @Override
        public char[] getPassword() {
            return password;
        }

        @Override
        public Set<String> getRoles() {
            return roles;
        }

        @Override
        public String getLogin() {
            return login;
        }
    }
}
