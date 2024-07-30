/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.gh8349;

import io.helidon.http.Status;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
class Gh8349Test {
    private final WebTarget target;

    @Inject
    Gh8349Test(WebTarget target) {
        this.target = target;
    }

    @Test
    void testApp1() {
        try (Response response = target.path("/greet1")
                .request()
                .post(Entity.text("hello"))) {
            assertThat(response.getStatus(), is(Status.OK_200.code()));

            String entity = response.readEntity(String.class);
            assertThat(entity, is("Hello World 1!"));
        }

    }

    @Test
    void testApp2() {
        try (Response response = target.path("/greet2")
                .request()
                .post(Entity.text("hello"))) {
            assertThat(response.getStatus(), is(Status.OK_200.code()));

            String entity = response.readEntity(String.class);
            assertThat(entity, is("Hello World 2!"));
        }
    }
}