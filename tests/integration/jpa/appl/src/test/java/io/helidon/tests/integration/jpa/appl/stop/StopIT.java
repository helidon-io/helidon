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
package io.helidon.tests.integration.jpa.appl.stop;

import java.util.logging.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

/**
 * Stop JPA MP test application.
 */
public class StopIT {

    /* Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(StopIT.class.getName());

    // FIXME: Use random port.
    private static final String TARGET = "http://localhost:7001";
     
    @Test
    public void testServer() {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(TARGET);
        WebTarget exit = target.path("/test/exit");
        Response response = exit.request().get();
        LOGGER.info(() -> String.format("Status: %s", response.readEntity(String.class)));
    }

}
