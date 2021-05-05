/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * REST Resource for JDBC test application.
 */
@Path("/testJdbc")
@RequestScoped
public class JdbcTestResource {

    @Inject
    private JdbcApiIT jdbcApiIt;

    private Dispatcher.Handle getHandle(final String name) {
        final int nameLen = name.length();
        final int serpPos = name.indexOf('.');
        final TestResult result = new TestResult();
        result.name(name);
        if (serpPos < 0 || (serpPos + 1) >= nameLen) {
            result.fail("Invalid test identifier: " + name);
            return null;
        }
        final String className = name.substring(0, serpPos);
        final String methodName = name.substring(serpPos + 1, nameLen);
        if ("JdbcApiIT".equals(className)) {
            try {
                return new Dispatcher.Handle(
                        jdbcApiIt,
                        JdbcApiIT.class.getDeclaredMethod(methodName, TestResult.class),
                        result);
            } catch (NoSuchMethodException ex) {
                result.throwed(ex);
            }
        } else {
            result.fail("Unknown test class: " + className);
        }
        return null;
    }

    /**
     * Run test identified by it's name ({@code<class>.<method>}).
     *
     * @param name name of the test
     * @return test execution result
     */
    private TestResult runTest(final String name) {
        Dispatcher.Handle handle = getHandle(name);
        if (handle == null) {
            return handle.result().fail("Missing method handle.");
        }
        return handle.invoke();
    }

    /**
     * Test setup invocation.
     *
     * @return test result
     */
    @GET
    @Path("/setup")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject setup() {
        return runTest("JdbcApiIT.setup").build();
    }

    /**
     * Test cleanup invocation.
     *
     * @return test result
     */
    @GET
    @Path("/destroy")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject destroy() {
        return runTest("JdbcApiIT.destroy").build();
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
    public JsonObject test(@PathParam("name") String name) {
        return runTest(name).build();
    }

}
