/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.microstream.greetings.mp;

import java.nio.file.Path;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.client.WebTarget;

import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

@HelidonTest
class MicrostreamExampleGreetingsMpTest {

    @Inject
    private WebTarget webTarget;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void beforeAll() {
        System.setProperty("one.microstream.storage.greetings.storage-directory", tempDir.toString());
    }

    @Test
    void testGreeting() {
        JsonObject response = webTarget.path("/greet").request().get(JsonObject.class);

        assertEquals("Hello World!", response.getString("message"), "response should be 'Hello World' ");
    }

}
