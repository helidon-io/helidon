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

package io.helidon.microprofile.example.helloworld.implicit;

import java.net.URI;
import java.util.logging.Logger;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.helidon.config.Config;
import io.helidon.microprofile.example.helloworld.implicit.cdi.LoggerQualifier;
import io.helidon.microprofile.example.helloworld.implicit.cdi.RequestId;
import io.helidon.microprofile.example.helloworld.implicit.cdi.ResourceProducer;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Resource for hello world example.
 */
@Path("helloworld")
@RequestScoped
public class HelloWorldResource {
    private final Config config;
    private final Logger logger;
    private final int requestId;
    private final String applicationName;
    private final URI applicationUri;
    private BeanManager beanManager;

    /**
     * Using constructor injection for field values.
     *
     * @param config      configuration instance
     * @param logger      logger (from {@link ResourceProducer}
     * @param requestId   requestId (from {@link ResourceProducer}
     * @param appName     name from configuration (app.name)
     * @param appUri      URI from configuration (app.uri)
     * @param beanManager bean manager (injected automatically by CDI)
     */
    @Inject
    public HelloWorldResource(Config config,
                              @LoggerQualifier Logger logger,
                              @RequestId int requestId,
                              @ConfigProperty(name = "app.name") String appName,
                              @ConfigProperty(name = "app.uri") URI appUri,
                              BeanManager beanManager) {
        this.config = config;
        this.logger = logger;
        this.requestId = requestId;
        this.applicationName = appName;
        this.applicationUri = appUri;
        this.beanManager = beanManager;
    }

    /**
     * Get method for this resource, shows logger and request id.
     *
     * @return hello world
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String message() {
        return "Hello World: " + logger + ", request: " + requestId + ", appName: " + applicationName;
    }

    /**
     * Get method for this resource, returning JSON.
     *
     * @param name name to add to response
     * @return JSON structure with injected fields
     */
    @Path("/{name}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getHello(@PathParam("name") String name) {
        return Json.createObjectBuilder()
                .add("name", name)
                .add("requestId", requestId)
                .add("appName", applicationName)
                .add("appUri", String.valueOf(applicationUri))
                .add("config", config.get("server.port").asInt())
                .add("beanManager", beanManager.toString())
                .add("logger", logger.getName())
                .build();
    }
}
