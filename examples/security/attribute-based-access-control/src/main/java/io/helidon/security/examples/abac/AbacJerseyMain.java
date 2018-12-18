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

package io.helidon.security.examples.abac;

import java.time.DayOfWeek;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import io.helidon.common.Builder;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SubjectType;
import io.helidon.security.abac.AbacProvider;
import io.helidon.security.abac.policy.PolicyValidator.PolicyStatement;
import io.helidon.security.abac.role.RoleValidator.Roles;
import io.helidon.security.abac.scope.ScopeValidator.Scope;
import io.helidon.security.abac.time.TimeValidator.DaysOfWeek;
import io.helidon.security.abac.time.TimeValidator.TimeOfDay;
import io.helidon.security.annot.Authenticated;
import io.helidon.security.annot.Authorized;
import io.helidon.security.jersey.SecurityFeature;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;

/**
 * Jersey example for Attribute based access control.
 */
public final class AbacJerseyMain {
    private static final int START_TIMEOUT_SECONDS = 10;

    private static WebServer server;

    private AbacJerseyMain() {
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

        server = startIt(routing);
    }

    static WebServer startIt(Builder<? extends Routing> routing) {
        WebServer server = WebServer.create(routing);

        long t = System.nanoTime();

        CountDownLatch cdl = new CountDownLatch(1);

        server.start().thenAccept(webServer -> {
            long time = System.nanoTime() - t;

            System.out.printf("Server started in %d ms%n", TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS));
            System.out.printf("Started server on localhost:%d%n", webServer.port());
            System.out.println();
            System.out.println("***********************");
            System.out.println("** Endpoints:        **");
            System.out.println("***********************");
            System.out.println("Using declarative authorization (ABAC):");
            System.out.printf("  http://localhost:%1$d/rest/attributes%n", server.port());
            System.out.println("Using explicit authorization (ABAC):");
            System.out.printf("  http://localhost:%1$d/rest/explicit%n", server.port());

            cdl.countDown();
        });

        try {
            cdl.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to start server within defined timeout: " + START_TIMEOUT_SECONDS + " seconds");
        }

        return server;
    }

    private static SecurityFeature buildSecurity() {
        return SecurityFeature.builder(Security.builder()
                                               // add the security provider to use
                                               .addProvider(AtnProvider::new)
                                               .addProvider(AbacProvider.create())
                                               .build())
                // we want to see why we were not authorized
                .debug()
                .build();
    }

    private static JerseySupport buildJersey() {
        return JerseySupport.builder()
                // register JAX-RS resource
                .register(AbacResource.class)
                // register JAX-RS resource with explicit authorization call
                .register(AbacExplicit.class)
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
     * Annotation only resource.
     */
    @Path("/attributes")
    @TimeOfDay(from = "08:15:00", to = "12:00:00")
    @TimeOfDay(from = "12:30:00", to = "17:30:00")
    @DaysOfWeek({DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY})
    @Scope("calendar_read")
    @Scope("calendar_edit")
    @Roles("user_role")
    @Roles(value = "service_role", subjectType = SubjectType.SERVICE)
    @PolicyStatement("${env.time.year >= 2017}")
    @Authenticated
    public static class AbacResource {
        /**
         * A resource method to demonstrate if access was successful or not.
         *
         * @return "hello"
         */
        @GET
        @AtnProvider.Authentication(value = "user",
                                    roles = {"user_role"},
                                    scopes = {"calendar_read", "calendar_edit"})
        @AtnProvider.Authentication(value = "service",
                                    type = SubjectType.SERVICE,
                                    roles = {"service_role"},
                                    scopes = {"calendar_read", "calendar_edit"})
        public String process() {
            return "hello";
        }

        /**
         * A resource method to demonstrate if access was successful or not.
         *
         * @return "hello"
         */
        @GET
        @Path("/deny")
        @PolicyStatement("${env.time.year < 2017}")
        @AtnProvider.Authentication(value = "user", scopes = {"calendar_read"})
        @AtnProvider.Authentication(value = "service",
                                    type = SubjectType.SERVICE,
                                    roles = {"service_role"},
                                    scopes = {"calendar_read", "calendar_edit"})
        public String deny() {
            return "hello";
        }
    }

    /**
     * Explicit authorization resource - authorization must be called by programmer.
     */
    @Path("/explicit")
    @TimeOfDay(from = "08:15:00", to = "12:00:00")
    @TimeOfDay(from = "12:30:00", to = "17:30:00")
    @DaysOfWeek({DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY})
    @Scope("calendar_read")
    @Scope("calendar_edit")
    @PolicyStatement("${env.time.year >= 2017 && object.owner == subject.principal.id}")
    @Authenticated
    public static class AbacExplicit {
        /**
         * A resource method to demonstrate explicit authorization.
         *
         * @param context  security context (injected)
         * @return "fine, sir" string; or a description of authorization failure
         */
        @GET
        @Authorized(explicit = true)
        @AtnProvider.Authentication(value = "user",
                                    roles = {"user_role"},
                                    scopes = {"calendar_read", "calendar_edit"})
        @AtnProvider.Authentication(value = "service",
                                    type = SubjectType.SERVICE,
                                    roles = {"service_role"},
                                    scopes = {"calendar_read", "calendar_edit"})
        public Response process(@Context SecurityContext context) {
            SomeResource res = new SomeResource("user");
            AuthorizationResponse atzResponse = context.authorize(res);

            if (atzResponse.isPermitted()) {
                //do the update
                return Response.ok().entity("fine, sir").build();
            } else {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(atzResponse.getDescription().orElse("Access not granted"))
                        .build();
            }
        }

        /**
         * A resource method to demonstrate explicit authorization - this should fail, as we do not call authorization.
         *
         * @param context security context (injected)
         * @return "fine, sir" string; or a description of authorization failure
         */
        @GET
        @Path("/deny")
        @Authorized(explicit = true)
        @AtnProvider.Authentication(value = "user",
                                    roles = {"user_role"},
                                    scopes = {"calendar_read", "calendar_edit"})
        @AtnProvider.Authentication(value = "service",
                                    type = SubjectType.SERVICE,
                                    roles = {"service_role"},
                                    scopes = {"calendar_read", "calendar_edit"})
        public Response fail(@Context SecurityContext context) {
            return Response.ok("This should not work").build();
        }
    }

    /**
     * Example resource.
     */
    public static class SomeResource {
        private String id;
        private String owner;
        private String message;

        private SomeResource(String owner) {
            this.id = "id";
            this.owner = owner;
            this.message = "Unit test";
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
