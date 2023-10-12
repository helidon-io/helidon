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

package io.helidon.tests.functional.requestscopecdi;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@HelidonTest
class SecretTest {

    @Inject
    private WebTarget baseTarget;

    @Test
    public void testSecrets() {
        Response r = baseTarget.path("greet")
                .request()
                .accept(MediaTypes.APPLICATION_JSON.text())
                .get();
        assertThat(r.getStatus(), is(Response.Status.OK.getStatusCode()));
        JsonObject o = r.readEntity(JsonObject.class);
        assertEquals(o.get("secret1"), o.get("secret2"));
    }
}
