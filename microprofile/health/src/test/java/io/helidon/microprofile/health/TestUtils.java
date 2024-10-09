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
package io.helidon.microprofile.health;

import io.helidon.common.http.Http;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class TestUtils {
    static JsonObject getLivenessCheck(JsonObject health, String checkName) {
        return health.getJsonArray("checks").stream()
                .map(JsonValue::asJsonObject)
                .filter(jo -> jo.getString("name").equals(checkName))
                .findFirst()
                .orElse(null);
    }

    static void checkForFailure(WebTarget webTarget) {
        Response response = webTarget.path("/health")
                .request()
                .get();

        assertThat("Health endpoint status", response.getStatus(), is(Http.Status.SERVICE_UNAVAILABLE_503.code()));

        JsonObject healthJson = response.readEntity(JsonObject.class);
        JsonObject diskSpace = TestUtils.getLivenessCheck(healthJson, "diskSpace");
        JsonObject heapMemory = TestUtils.getLivenessCheck(healthJson, "heapMemory");
        assertThat("Disk space liveness return data", diskSpace, is(notNullValue()));
        assertThat("Disk space liveness check status", diskSpace.getString("status"), is("DOWN"));
        assertThat("Heap memory liveness return data", heapMemory, is(notNullValue()));
        assertThat("Heap memory liveness check status", heapMemory.getString("status"), is("DOWN"));
    }
}
