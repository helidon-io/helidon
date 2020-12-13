/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.jpa.appl.test;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.stream.JsonParsingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

/**
 * REST client utilities for remote test calls.
 */
public class ClientUtils {
    
    private static final Logger LOGGER = Logger.getLogger(ClientUtils.class.getName());

    private ClientUtils() {
        throw new UnsupportedOperationException("Instances of ClientUtils class are not allowed");
    }

    private static final Client CLIENT = ClientBuilder.newClient();
    // FIXME: Use random port.
    private static final WebTarget TARGET = CLIENT.target("http://localhost:7001/test");
    private static final WebTarget TARGET_JDBC = CLIENT.target("http://localhost:7001/testJdbc");

    /**
     * Call remote test on MP server using REST interface.
     *
     * @param path test path (URL suffix {@code <class>.<method>})
     */
    public static void callTest(final String path) {
        WebTarget status = TARGET.path(path);
        Response response = status.request().get();
        String responseStr = response.readEntity(String.class);
        try {
            Validate.check(responseStr);
        } catch (JsonParsingException t) {
            LOGGER.log(Level.SEVERE, t, () -> String.format("Response is not JSON: %s, message: %s", t.getMessage(), responseStr));
        }
    }

    /**
     * Call remote test on MP server using REST interface.
     *
     * @param path test path (URL suffix {@code <class>.<method>})
     */
    public static void callJdbcTest(final String path) {
        WebTarget status = TARGET_JDBC.path(path);
        Response response = status.request().get();
        String responseStr = response.readEntity(String.class);
        try {
            Validate.check(responseStr);
        } catch (JsonParsingException t) {
            LOGGER.log(Level.SEVERE, t, () -> String.format("Response is not JSON: %s, message: %s", t.getMessage(), responseStr));
        }
    }

}
