/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.security.integration.jersey.SecurityFeature;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;

/**
 * Example of integration between Jersey and Security module using config,
 * yet no container security - all security enforcement is by hand.
 */
public final class JerseyProgrammaticMain {
    private static volatile WebServer server;

    private JerseyProgrammaticMain() {
    }

    private static SecurityFeature buildSecurity() {
        return new SecurityFeature(Security.create(Config.create().get("security")));
    }

    private static JerseySupport buildJersey() {
        return JerseySupport.builder()
                // register JAX-RS resource
                .register(JerseyResources.HelloWorldProgrammaticResource.class)
                // register JAX-RS resource demonstrating identity propagation (propagation
                // itself is programmatic only, this resource uses annotation to protect itself
                .register(JerseyResources.OutboundSecurityResource.class)
                // integrate security
                .register(buildSecurity())
                .register(new ExceptionMapper<Exception>() {
                    @Override
                    public Response toResponse(Exception exception) {
                        if (exception instanceof WebApplicationException) {
                            return ((WebApplicationException) exception).getResponse();
                        }
                        exception.printStackTrace();
                        return Response.serverError().build();
                    }
                })
                .build();
    }

    static WebServer getHttpServer() {
        return server;
    }

    /**
     * Main method of example. No arguments required, no configuration required.
     *
     * @param args empty is OK
     */
    public static void main(String[] args) {
        Routing.Builder routing = Routing.builder()
                .register("/rest", buildJersey());

        server = JerseyUtil.startIt(routing, 8080);

        JerseyResources.setPort(server.port());
    }

}
