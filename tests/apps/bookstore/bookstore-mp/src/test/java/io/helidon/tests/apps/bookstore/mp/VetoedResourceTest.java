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

package io.helidon.tests.apps.bookstore.mp;

import javax.inject.Inject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test that the resource class {@link VetoedResource} is not part
 * of the application after it has been vetoed by {@link VetoCdiExtension}.
 */
@HelidonTest
class VetoedResourceTest {

    @Inject
    private WebTarget webTarget;

    @Test
    void testVetoed() {
        Response res = webTarget.path("/vetoed").request().get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), res.getStatus());
    }
}
