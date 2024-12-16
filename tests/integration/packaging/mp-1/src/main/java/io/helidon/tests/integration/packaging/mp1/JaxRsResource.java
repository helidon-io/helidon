/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.packaging.mp1;

import java.lang.System.Logger.Level;
import java.lang.reflect.Field;

import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Providers;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * A resource to test.
 */
@Path("/")
public class JaxRsResource {
    private static final System.Logger LOGGER = System.getLogger(JaxRsResource.class.getName());

    @Inject
    @ConfigProperty(name = "app.jaxrs.message")
    private String jaxRsMessage;
    @Inject
    @ConfigProperty(name = "app.jaxrs.number")
    private int number;
    @Inject
    @ConfigProperty(name = "app.message")
    private String message;

    @Inject
    private BeanClass.BeanType beanType;

    // we need to test injection of all injectable JAX-RS classes
    @Context
    private UriInfo uriInfo;

    @Context
    private Application application;

    @Context
    private Configuration configuration;

    @Context
    private Providers providers;

    @Context
    private HttpHeaders httpHeaders;

    @Context
    private Request request;

    @Context
    private SecurityContext securityContext;

    @Context
    private ResourceContext resourceContext;

    @GET
    @Path("/fields")
    public String getFields() {
        StringBuilder problems = new StringBuilder();
        Field[] declaredFields = JaxRsResource.class.getDeclaredFields();
        for (Field declaredField : declaredFields) {
            declaredField.setAccessible(true);
            try {
                Object value = declaredField.get(this);
                if (null == value) {
                    problems.append("\nField ")
                            .append(declaredField.getName())
                            .append(" is null. Should have been injected");
                }
            } catch (Exception e) {
                problems.append("\nError: ")
                        .append(declaredField.getName())
                        .append(" get failed with exception. Class: ")
                        .append(e.getClass().getName())
                        .append(", message: ")
                        .append(e.getMessage());
            }
        }

        if (problems.length() > 0) {
            throw new IllegalStateException(problems.toString());
        }

        return "All injected correctly";
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @APIResponse(name="hello", responseCode = "200", description = "Hello world message")
    public String hello() {
        LOGGER.log(Level.INFO, "This message is here to make sure runtime logging configuration works");
        return "Hello World " + uriInfo.getPath();
    }

    @GET
    @Path("/property")
    @APIResponse(name="normal", responseCode = "200", description = "Value of property 'app.message' from config.")
    public String message() {
        return message;
    }

    @GET
    @Path("/beantype")
    public String beanType() {
        return beanType.message();
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

    @GET
    @Path("/queryparam")
    public String queryParam(@QueryParam("long") Long longParam) {
        return String.valueOf(longParam);
    }

    @GET
    @Path("/queryparam2")
    public String queryParam(@QueryParam("boolean") Boolean booleanParam) {
        return String.valueOf(booleanParam);
    }

    @GET
    @Path("/queryparam3")
    public String queryParam(@QueryParam("int") int intParam) {
        return String.valueOf(intParam);
    }
}
