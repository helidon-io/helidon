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

package io.helidon.tests.functional.requestscopecdi;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.common.http.MediaType;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.netty.handler.codec.http.HttpResponseStatus;
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
                .accept(MediaType.APPLICATION_JSON.toString())
                .get();
        assertThat(r.getStatus(), is(HttpResponseStatus.OK.code()));
        JsonObject o = r.readEntity(JsonObject.class);
        assertEquals(o.get("secret1"), o.get("secret2"));
    }
}
