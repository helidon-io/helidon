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

package io.helidon.examples.security.digest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import io.helidon.security.providers.httpauth.HttpDigest;
import io.helidon.security.providers.httpauth.HttpDigestAuthProvider;
import io.helidon.security.providers.httpauth.SecureUserStore;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.security.SecurityFeature;

/**
 * Example of HTTP digest authentication with WebServer fully configured programmatically.
 */
@SuppressWarnings("SpellCheckingInspection")
public final class DigestExampleBuilderMain {
    // simple approach to user storage - for real world, use data store...
    private static final Map<String, MyUser> USERS = new HashMap<>();

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    static {
        USERS.put("jack", new MyUser("jack", "password".toCharArray(), Set.of("user", "admin")));
        USERS.put("jill", new MyUser("jill", "password".toCharArray(), Set.of("user")));
        USERS.put("john", new MyUser("john", "password".toCharArray(), Set.of()));
    }

    private DigestExampleBuilderMain() {
    }

    /**
     * Starts this example. Programmatic configuration. See standard output for instructions.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        LogConfig.configureRuntime();

        WebServerConfig.Builder builder = WebServer.builder();
        setup(builder);
        WebServer server = builder.build();

        long t = System.nanoTime();
        server.start();
        long time = System.nanoTime() - t;

        System.out.printf("""
                Server started in %d ms

                Started server on localhost:%2$d

                Users:
                jack/password in roles: user, admin
                jill/password in roles: user
                john/password in no roles

                ***********************
                ** Endpoints:        **
                ***********************

                No authentication: http://localhost:%2$d/public
                No roles required, authenticated: http://localhost:%2$d/noRoles
                User role required: http://localhost:%2$d/user
                Admin role required: http://localhost:%2$d/admin
                Always forbidden (uses role nobody is in), audited: http://localhost:%2$d/deny
                Admin role required, authenticated, authentication optional, audited \
                (always forbidden - challenge is not returned as authentication is optional): http://localhost:%2$d/noAuthn

                """, TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS), server.port());
    }

    static void setup(WebServerConfig.Builder server) {
        server.featuresDiscoverServices(false)
                .addFeature(SecurityFeature.builder()
                                    .security(security())
                                    .defaults(SecurityFeature.authenticate())
                                    .build())
                .routing(routing -> routing
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

    private static Security security() {
        return Security.builder()
                .addAuthenticationProvider(
                        HttpDigestAuthProvider.builder()
                                .realm("mic")
                                .digestServerSecret("aPassword".toCharArray())
                                .userStore(buildUserStore()),
                        "digest-auth")
                .build();
    }

    private static SecureUserStore buildUserStore() {
        return login -> Optional.ofNullable(USERS.get(login));
    }

    private record MyUser(String login, char[] password, Set<String> roles) implements SecureUserStore.User {

        private static String bytesToHex(byte[] bytes) {
            char[] hexChars = new char[bytes.length * 2];
            for (int j = 0; j < bytes.length; j++) {
                int v = bytes[j] & 0xFF;
                hexChars[j * 2] = HEX_ARRAY[v >>> 4];
                hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
            }
            return new String(hexChars);
        }

        @Override
        public boolean isPasswordValid(char[] password) {
            return Arrays.equals(password(), password);
        }

        @Override
        public Optional<String> digestHa1(String realm, HttpDigest.Algorithm algorithm) {
            if (algorithm != HttpDigest.Algorithm.MD5) {
                throw new IllegalArgumentException("Unsupported algorithm " + algorithm);
            }
            String a1 = login + ":" + realm + ":" + new String(password());
            byte[] bytes = a1.getBytes(StandardCharsets.UTF_8);
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("MD5 algorithm should be supported", e);
            }
            return Optional.of(bytesToHex(digest.digest(bytes)));
        }
    }
}
