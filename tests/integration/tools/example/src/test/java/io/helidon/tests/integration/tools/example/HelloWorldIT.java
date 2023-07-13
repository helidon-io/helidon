/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.tools.example;

import java.lang.System.Logger.Level;
import java.util.Map;

import io.helidon.common.LazyValue;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.HelidonTestException;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test Hello World service.
 */
class HelloWorldIT {

    private static final System.Logger LOGGER = System.getLogger(HelloWorldIT.class.getName());

    private final LazyValue<TestServiceClient> testClient = LazyValue.create(() -> TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("HelloWorld")
            .build());

    // Test sendHelloWorld service
    @Test
    void testHelloWorld() {
        LOGGER.log(Level.DEBUG, () -> "Running testHelloWorld");
        JsonValue data = testClient.get().callServiceAndGetData("sendHelloWorld");
        String helloWorld = ((JsonString) data).getString();
        LOGGER.log(Level.TRACE, () -> String.format("Response: \"%s\"", helloWorld));
        assertThat(helloWorld, equalTo("Hello World!"));
    }

    // Test verifyHello service with expected positive response
    @Test
    void testVerifyHelloPositive() {
        LOGGER.log(Level.DEBUG, "Running testVerifyHelloPositive");
        try {
            testClient.get().callServiceAndGetData("verifyHello", Map.of("value", "Hello World!"));
        } catch (HelidonTestException te) {
            fail(String.format(
                    "Caught %s: %s",
                    te.getClass().getSimpleName(),
                    te.getMessage()), te);
        }
    }

    // Test verifyHello service with expected negative response
    @Test
    void testVerifyHelloNegative() {
        LOGGER.log(Level.DEBUG, "Running testVerifyHelloNegative");
        try {
            testClient.get().callServiceAndGetData("verifyHello", Map.of("value", "Wrong content."));
            fail("HelidonTestException was not thrown");
        } catch (HelidonTestException te) {
            LOGGER.log(Level.TRACE, () -> String.format(
                    "Got expected %s: %s",
                    te.getClass().getSimpleName(),
                    te.getMessage()));
        }
    }

    // Test personalHelloWorld service with name from database
    @Test
    void testPersonalHelloWorld() {
        LOGGER.log(Level.DEBUG, "Running testPersonalHelloWorld");
        JsonValue data = testClient.get()
                .callServiceAndGetData(
                        "personalHelloWorld",
                        Map.of("nick", "Ash"));
        String greeting = ((JsonString) data).getString();
        LOGGER.log(Level.TRACE, () -> String.format("Response: \"%s\"", greeting));
        assertThat(greeting, containsString("Ash Ketchum"));
    }
}
