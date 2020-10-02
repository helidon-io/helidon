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

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

/**
 * REST client utilities for remote test calls.
 */
public class ClientUtils {
    
    private ClientUtils() {
        throw new UnsupportedOperationException("Instances of ClientUtils class are not allowed");
    }

    private static final Client CLIENT = ClientBuilder.newClient();
    private static final WebTarget TARGET = CLIENT.target("http://localhost:7001/test");

    /**
     * Call remote test on MP server using REST interface.
     *
     * @param path test path (URL suffix {@code <class>.<method>})
     */
    public static void callTest(final String path) {
        WebTarget status = TARGET.path(path);
        Response response = status.request().get();
        Validate.check(response.readEntity(String.class));
    }

}
