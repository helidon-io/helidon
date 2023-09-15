/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

import io.helidon.examples.integrations.micronaut.data.model.Pet;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
class MicronautExampleTest {
    @Inject
    private WebTarget webTarget;

    @Test
    void testAllPets() {
        JsonArray jsonValues = webTarget.path("/pets")
                .request()
                .get(JsonArray.class);

        assertThat("We should get all pets", jsonValues.size(), is(3));
    }

    @Test
    void testGetPet() {
        JsonObject pet = webTarget.path("/pets/Dino")
                .request()
                .get(JsonObject.class);

        assertThat(pet.getString("name"), is("Dino"));
        assertThat(pet.getString("type"), is(Pet.PetType.DOG.toString()));
    }

    @Test
    void testNotFound() {
        try (Response response = webTarget.path("/pets/Fino")
                .request()
                .get()) {
            assertThat("Should be not found: 404", response.getStatus(), is(404));
        }
    }

    @Test
    void testValidationError() {
        try (Response response = webTarget.path("/pets/a")
                .request()
                .get()) {
            assertThat("Should be bad request: 400", response.getStatus(), is(400));
        }
    }

}