/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.examples.tutorial;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testsupport.TestClient;
import io.helidon.webserver.testsupport.TestResponse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link Main}.
 */
public class MainTest {

    @Test
    public void testShutDown() throws Exception {
        TestResponse response = TestClient.create(Main.createRouting())
                .path("/mgmt/shutdown")
                .post();
        assertEquals(Http.Status.OK_200, response.status());
        CountDownLatch latch = new CountDownLatch(1);
        WebServer webServer = response.webServer();
                webServer
                        .whenShutdown()
                        .thenRun(latch::countDown);
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
