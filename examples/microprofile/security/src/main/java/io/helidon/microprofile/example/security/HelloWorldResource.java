/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.example.security;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.annotations.Authenticated;
import io.helidon.security.annotations.Authorized;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A dynamic resource that shows a link to the static resource.
 */
@Path("/helloworld")
@RequestScoped
public class HelloWorldResource {
    @Inject
    private Security security;
    @Inject
    private SecurityContext securityContext;

    @Inject
    @ConfigProperty(name = "server.static.classpath.context")
    private String context;

    /**
     * Public page (does not require authentication).
     * If there is pre-emptive basic auth, it will run within a user context.
     *
     * @return web page with links to other resources
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    @Authenticated(optional = true)
    public String getPublic() {
        return "<html><head/><body>Hello World. This is a public page with no security "
                + "<a href=\"helloworld/admin\">Allowed for admin only</a><br>"
                + "<a href=\"helloworld/user\">Allowed for user only</a><br>"
                + "<a href=\"" + context + "/resource.html\">" + context + "/resource.html allowed for a logged in user</a><br>"
                + "you are logged in as: " + securityContext.user()
                + "</body></html>";
    }

    /**
     * Page restricted to users in "admin" role.
     *
     * @param securityContext Helidon security context
     * @return web page with links to other resources
     */
    @GET
    @Path("/admin")
    @Produces(MediaType.TEXT_HTML)
    @Authenticated
    @Authorized
    @RolesAllowed("admin")
    public String getAdmin(@Context SecurityContext securityContext) {
        return "<html><head/><body>Hello World. You may want to check "
                + "<a href=\"" + context + "/resource.html\">" + context + "/resource.html</a><br>"
                + "you are logged in as: " + securityContext.user()
                + "</body></html>";
    }

    /**
     * Page restricted to users in "user" role.
     *
     * @param securityContext Helidon security context
     * @return web page with links to other resources
     */
    @GET
    @Path("/user")
    @Produces(MediaType.TEXT_HTML)
    @Authenticated
    @Authorized
    @RolesAllowed("user")
    public String getUser(@Context SecurityContext securityContext) {
        return "<html><head/><body>Hello World. You may want to check "
                + "<a href=\"" + context + "/resource.html\">" + context + "/resource.html</a><br>"
                + "you are logged in as: " + securityContext.user()
                + "</body></html>";
    }
}
