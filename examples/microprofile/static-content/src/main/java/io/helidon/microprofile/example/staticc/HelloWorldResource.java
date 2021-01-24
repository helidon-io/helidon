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

package io.helidon.microprofile.example.staticc;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A dynamic resource that shows a link to the static resource.
 */
@Path("/helloworld")
@RequestScoped
public class HelloWorldResource {
    @Inject
    @ConfigProperty(name = "server.static.classpath.context", defaultValue = "${EMPTY}")
    private String context;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String getIt() {
        return "<html><head/><body>Hello World. You may want to check "
                + "<a href=\"" + context + "/resource.html\">" + context + "/resource.html</a>"
                + "</body></html>";
    }
}
