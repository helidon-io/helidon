
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

package io.helidon.security.examples.signatures;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.security.CompositeProviderFlag;
import io.helidon.security.CompositeProviderSelectionPolicy;
import io.helidon.security.Security;
import io.helidon.security.integration.nima.SecurityFeature;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.providers.httpauth.SecureUserStore;
import io.helidon.security.providers.httpsign.HttpSignProvider;
import io.helidon.security.providers.httpsign.InboundClientDefinition;
import io.helidon.security.providers.httpsign.OutboundTargetDefinition;

/**
 * Example of authentication of service with http signatures, using configuration file as much as possible.
 */
@SuppressWarnings("DuplicatedCode")
public class SignatureExampleBuilderMain {

    private static final Map<String, SecureUserStore.User> USERS = new HashMap<>();

    static {
        addUser("jack", "password", List.of("user", "admin"));
        addUser("jill", "password", List.of("user"));
        addUser("john", "password", List.of());
    }

    private SignatureExampleBuilderMain() {
    }

    private static void addUser(String user, String password, List<String> roles) {
        USERS.put(user, new SecureUserStore.User() {
            @Override
            public String login() {
                return user;
            }

            char[] password() {
                return password.toCharArray();
            }

            @Override
            public boolean isPasswordValid(char[] password) {
                return Arrays.equals(password(), password);
            }

            @Override
            public Collection<String> roles() {
                return roles;
            }
        });
    }

    /**
     * Starts this example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        // to allow us to set host header explicitly
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

        WebServerConfig.Builder builder = WebServer.builder();
        setup(builder);
        WebServer server = builder.build();

        long t = System.nanoTime();
        server.start();
        long time = System.nanoTime() - t;

        System.out.printf("""
                Server started in %1d ms

                Signature example: from builder

                "Users:
                jack/password in roles: user, admin
                jill/password in roles: user
                john/password in no roles

                ***********************
                ** Endpoints:        **
                ***********************

                Basic authentication, user role required, will use symmetric signatures for outbound:
                  http://localhost:9080/service1
                Basic authentication, user role required, will use asymmetric signatures for outbound:
                  http://localhost:8080/service1-rsa

                """, TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS));
    }

    static void setup(WebServerConfig.Builder server) {
        server.port(9080)
                .routing(SignatureExampleBuilderMain::routing2)
                .putSocket("service2", socket -> socket
                        .port(8080)
                        .routing(SignatureExampleBuilderMain::routing1));
    }

    private static void routing2(HttpRouting.Builder routing) {
        // helper method to load both security and web server security from configuration
        SecurityFeature security = SecurityFeature.create(security2())
                .securityDefaults(SecurityFeature.authenticate());

        routing.addFeature(security)
                .get("/service2*", SecurityFeature.rolesAllowed("user"))
                .register(new Service2());
    }

    private static void routing1(HttpRouting.Builder routing) {
        // build routing (security is loaded from config)
        SecurityFeature security = SecurityFeature.create(security1())
                .securityDefaults(SecurityFeature.authenticate());
        routing.addFeature(security)
                .get("/service1*", SecurityFeature.rolesAllowed("user"))
                .register(new Service1());
    }

    private static Security security2() {
        return Security.builder()
                .providerSelectionPolicy(CompositeProviderSelectionPolicy
                        .builder()
                        .addAuthenticationProvider("http-signatures", CompositeProviderFlag.OPTIONAL)
                        .addAuthenticationProvider("basic-auth")
                        .build())
                .addProvider(HttpBasicAuthProvider
                                .builder()
                                .realm("mic")
                                .userStore(users()),
                        "basic-auth")
                .addProvider(HttpSignProvider.builder()
                                .addInbound(InboundClientDefinition
                                        .builder("service1-hmac")
                                        .principalName("Service1 - HMAC signature")
                                        .hmacSecret("somePasswordForHmacShouldBeEncrypted")
                                        .build())
                                .addInbound(InboundClientDefinition
                                        .builder("service1-rsa")
                                        .principalName("Service1 - RSA signature")
                                        .publicKeyConfig(Keys.builder()
                                                .keystore(k -> k
                                                        .keystore(Resource.create(Paths.get("src/main/resources/keystore.p12")))
                                                        .passphrase("password".toCharArray())
                                                        .certAlias("service_cert")
                                                        .build())
                                                .build())
                                        .build()),
                        "http-signatures")
                .build();
    }

    private static Security security1() {
        return Security.builder()
                .providerSelectionPolicy(CompositeProviderSelectionPolicy
                        .builder()
                        .addOutboundProvider("basic-auth")
                        .addOutboundProvider("http-signatures")
                        .build())
                .addProvider(HttpBasicAuthProvider
                                .builder()
                                .realm("mic")
                                .userStore(users())
                                .addOutboundTarget(OutboundTarget.builder("propagate-all").build()),
                        "basic-auth")
                .addProvider(HttpSignProvider
                                .builder()
                                .outbound(OutboundConfig
                                        .builder()
                                        .addTarget(hmacTarget())
                                        .addTarget(rsaTarget())
                                        .build()),
                        "http-signatures")
                .build();
    }

    private static OutboundTarget rsaTarget() {
        return OutboundTarget.builder("service2-rsa")
                .addHost("localhost")
                .addPath("/service2-rsa.*")
                .customObject(OutboundTargetDefinition.class,
                        OutboundTargetDefinition.builder("service1-rsa")
                                .privateKeyConfig(Keys.builder()
                                        .keystore(k -> k
                                                .keystore(Resource.create(Paths.get("src/main/resources/keystore.p12")))
                                                .passphrase("password".toCharArray())
                                                .keyAlias("myPrivateKey")
                                                .build()))
                                .build())
                .build();
    }

    private static OutboundTarget hmacTarget() {
        return OutboundTarget.builder("service2")
                .addHost("localhost")
                .addPath("/service2")
                .customObject(
                        OutboundTargetDefinition.class,
                        OutboundTargetDefinition
                                .builder("service1-hmac")
                                .hmacSecret("somePasswordForHmacShouldBeEncrypted")
                                .build())
                .build();
    }

    private static SecureUserStore users() {
        return login -> Optional.ofNullable(USERS.get(login));
    }
}
