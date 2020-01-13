/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.nativeimage.mp1;

import java.util.logging.Logger;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A resource to test.
 */
@Path("/")
public class JaxRsResource {
    private static final Logger LOGGER = Logger.getLogger(JaxRsResource.class.getName());

    @Inject
    @ConfigProperty(name = "app.jaxrs.message")
    private String jaxRsMessage;
    @Inject
    @ConfigProperty(name = "app.jaxrs.number")
    private int number;
    @Inject
    @ConfigProperty(name = "app.message")
    private String message;

    @Context
    private UriInfo uriInfo;

    @GET
    public String hello() {
        LOGGER.info("This message is here to make sure runtime logging configuration works");
        return "Hello World " + uriInfo.getPath();
    }

    @GET
    @Path("/property")
    public String message() {
        return message;
    }

    @GET
    @Path("/jaxrsproperty")
    public String jaxRsMessage() {
        return jaxRsMessage;
    }

    @GET
    @Path("/number")
    public String number() {
        return String.valueOf(number);
    }

    @GET
    @Path("/jsonp")
    public JsonObject jsonObject() {
        return Json.createObjectBuilder()
                .add("message", "json-p")
                .build();
    }

    @GET
    @Path("/jsonb")
    @Produces(MediaType.APPLICATION_JSON)
    public TestDto jsonBinding() {
        return new TestDto("json-b");
    }
}
