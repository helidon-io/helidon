/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.common;

import java.lang.reflect.InvocationTargetException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import static java.util.Objects.requireNonNullElse;

/**
 * Resource to expose all tests.
 */
@Path("/test")
@ApplicationScoped
public class TestResource {

    @Inject
    private InsertTestImpl insert;

    @Inject
    private DeleteTestImpl delete;

    @Inject
    private UpdateTestImpl update;

    @Inject
    private QueryTestImpl query;

    @GET
    @Path("/insert/{testName}")
    public String insert(@PathParam("testName") String testName) {
        return invokeTest(insert, testName);
    }

    @GET
    @Path("/delete/{testName}")
    public String delete(@PathParam("testName") String testName) {
        return invokeTest(delete, testName);
    }

    @GET
    @Path("/update/{testName}")
    public String update(@PathParam("testName") String testName) {
        return invokeTest(update, testName);
    }

    @GET
    @Path("/query/{testName}")
    public String query(@PathParam("testName") String testName) {
        return invokeTest(query, testName);
    }

    private static String invokeTest(Object o, String testName) {
        try {
            o.getClass().getMethod(testName).invoke(o);
            return "OK";
        } catch (IllegalAccessException ex) {
            throw new InternalServerErrorException(ex);
        } catch (InvocationTargetException ex) {
            requireNonNullElse(ex.getCause(), ex).printStackTrace(System.err);
            throw new InternalServerErrorException();
        } catch (NoSuchMethodException ignored) {
            throw new NotFoundException();
        }
    }
}
