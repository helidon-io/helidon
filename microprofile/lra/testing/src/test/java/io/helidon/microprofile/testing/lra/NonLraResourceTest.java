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

package io.helidon.microprofile.testing.lra;

import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
@Path("/test/non-lra")
public class NonLraResourceTest {

    private final WebTarget target;

    @Inject
    public NonLraResourceTest(WebTarget target) {
        this.target = target;
    }

    @GET
    @Path("/say-hi")
    public String sayHi() {
        return "Hi!";
    }

    @Test
    public void testNonLraResource() {
        try (Response res = target
                .path("/test/non-lra/say-hi")
                .request()
                .get()) {
            assertThat(res.getStatus(), is(200));
            assertThat(res.readEntity(String.class), is("Hi!"));
        }
    }
}
