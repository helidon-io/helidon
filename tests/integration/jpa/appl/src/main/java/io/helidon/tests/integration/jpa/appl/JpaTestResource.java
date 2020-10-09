/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.appl;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.helidon.tests.integration.jpa.dao.Create;

/**
 * REST Resource for JPA test application.
 */
@Path("/test")
@RequestScoped
public class JpaTestResource {
    
    private static final Logger LOGGER = Logger.getLogger(JpaTestResource.class.getName());

    @PersistenceContext(unitName = "test")
    private EntityManager em;

    @Inject
    Dispatcher dispatcher;
    
    /**
     * Resource status check
     *
     * @return status message
     */
    @GET
    @Path("/status")
    public String status() {
        return "JPA test application is alive.";
    }

    /**
     * Test initialization.
     *
     * @return initialization result
     */
    @GET
    @Path("/init")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public JsonObject init() {
        final TestResult result = new TestResult();
        result.name("Initialization");
        try {
            Create.dbInsertTypes(em);
            result.message("Database was initialized");
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, t, () -> String.format("Pokemon types initialization failed: %s", t.getMessage()));
            result.throwed(t);
        }
        return result.build();
    }

    /**
     * Test whether MP application can access JPA Entity Beans.
     *
     * @return test result
     */
    @GET
    @Path("/beans")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject beans() {
        final TestResult result = new TestResult();
        result.name("Beans check");
        try {
            Class<?> typeClass = Class.forName("io.helidon.tests.integration.jpa.model.Type");
            Class<?> pokemonClass = Class.forName("io.helidon.tests.integration.jpa.model.Pokemon");
            Class<?> trainerClass = Class.forName("io.helidon.tests.integration.jpa.model.Trainer");
            result.assertNotNull(typeClass);
            result.assertNotNull(pokemonClass);
            result.assertNotNull(trainerClass);
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, t, () -> String.format("JPA Entity beans check failed: %s", t.getMessage()));
            result.throwed(t);
        }
        return result.build();
    }

    /**
     * Test invocation.
     *
     * @param name test name
     * @return test result
     */
    @GET
    @Path("/test/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public JsonObject test(@PathParam("name") String name) {
        TestResult result = dispatcher.runTest(name);
        return result.build();
    }

    /**
     * Terminate JPA MP application.
     *
     * @return shutdown message
     */
    @GET
    @Path("/exit")
    public String exit() {
        ExitThread.start();
        return "JPA MP application shutting down.";
    }

}
