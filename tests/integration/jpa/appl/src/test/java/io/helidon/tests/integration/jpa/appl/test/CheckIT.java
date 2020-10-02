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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Initialize tests.
 */
public class CheckIT {

    /** Startup timeout in seconds. */
    private static final int TIMEOUT = 60;

    private static final Client CLIENT = ClientBuilder.newClient();
    private static final WebTarget TARGET = CLIENT.target("http://localhost:7001/test");

    @BeforeAll
    @SuppressWarnings("SleepWhileInLoop")
    public static void waitForServer() {
        WebTarget status = TARGET.path("/status");

        long tmEnd = System.currentTimeMillis() + (TIMEOUT * 1000);
        boolean retry = true;
        while (retry) {
            try {
                Response response = status.request().get();
                System.out.println("STATUS: " + response.readEntity(String.class));
                retry = false;
            } catch (Exception ex) {
                System.out.println("STATUS: " + ex.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    System.out.println("STATUS: " + ie.getMessage());
                }
                if (System.currentTimeMillis() > tmEnd) {
                    System.out.println("STATUS: Startup timeout");
                    retry = false;
                }
            }
        }
    }

    @Test
    public void testInit() {
        WebTarget status = TARGET.path("/init");
        Response response = status.request().get();
        Validate.check(response.readEntity(String.class));
    }

    @Test
    public void testBeans() {
        WebTarget status = TARGET.path("/beans");
        Response response = status.request().get();
        Validate.check(response.readEntity(String.class));
    }

}
