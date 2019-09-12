/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.examples.jersey;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import io.helidon.common.CollectionsHelper;
import io.helidon.security.Security;
import io.helidon.security.integration.jersey.SecurityFeature;
import io.helidon.security.providers.abac.AbacProvider;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.providers.httpauth.UserStore;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;

/**
 * Example of integration between Jersey and Security module using builders.
 */
public final class JerseyBuilderMain {
    private static final Map<String, UserStore.User> USERS = new HashMap<>();
    private static volatile WebServer server;

    static {
        addUser("jack", "password", CollectionsHelper.listOf("user", "admin"));
        addUser("jill", "password", CollectionsHelper.listOf("user"));
        addUser("john", "password", CollectionsHelper.listOf());
    }

    private JerseyBuilderMain() {
    }

    private static void addUser(String user, String password, List<String> roles) {
        USERS.put(user, new UserStore.User() {
            @Override
            public String login() {
                return user;
            }

            @Override
            public char[] password() {
                return password.toCharArray();
            }

            @Override
            public Collection<String> roles() {
                return roles;
            }
        });
    }

    static WebServer getHttpServer() {
        return server;
    }

    private static SecurityFeature buildSecurity() {
        return new SecurityFeature(
                Security.builder()
                        // add the security provider to use
                        .addProvider(HttpBasicAuthProvider.builder()
                                             .realm("helidon")
                                             .userStore(users()))
                        .addProvider(AbacProvider.create())
                        .build());
    }

    private static UserStore users() {
        return login -> Optional.ofNullable(USERS.get(login));
    }

    private static JerseySupport buildJersey() {
        return JerseySupport.builder()
                // register JAX-RS resource
                .register(JerseyResources.HelloWorldResource.class)
                // register JAX-RS resource demonstrating identity propagation
                .register(JerseyResources.OutboundSecurityResource.class)
                // integrate security
                .register(buildSecurity())
                .register(new ExceptionMapper<Exception>() {
                    @Override
                    public Response toResponse(Exception exception) {
                        exception.printStackTrace();
                        return Response.serverError().build();
                    }
                })
                .build();
    }

    /**
     * Main method of example. No arguments required, no configuration required.
     *
     * @param args empty is OK
     * @throws Throwable if server fails to start
     */
    public static void main(String[] args) throws Throwable {
        Routing.Builder routing = Routing.builder()
                .register("/rest", buildJersey());

        server = JerseyUtil.startIt(routing, 8080);

        JerseyResources.setPort(server.port());
    }
}
