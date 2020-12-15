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

package io.helidon.examples.integrations.micronaut.data;

import javax.inject.Inject;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.examples.integrations.micronaut.data.model.Pet;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@HelidonTest
class MicronautExampleTest {
    static {
        System.setProperty("oracle.jdbc.fanEnabled", "false");
    }

    @Inject
    private WebTarget webTarget;

    @Test
    void testAllPets() {
        JsonArray jsonValues = webTarget.path("/pets")
                .request()
                .get(JsonArray.class);

        assertEquals(3, jsonValues.size(), "We should get all pets");
    }

    @Test
    void testGetPet() {
        JsonObject pet = webTarget.path("/pets/Dino")
                .request()
                .get(JsonObject.class);

        assertEquals("Dino", pet.getString("name"));
        assertEquals(Pet.PetType.DOG.toString(), pet.getString("type"));
    }

    @Test
    void testNotFound() {
        Response response = webTarget.path("/pets/Fino")
                .request()
                .get();

        assertEquals(404, response.getStatus(), "Should be not found: 404");
    }

    @Test
    void testValidationError() {
        Response response = webTarget.path("/pets/a")
                .request()
                .get();

        assertEquals(400, response.getStatus(), "Should be bad request: 400");
    }

}