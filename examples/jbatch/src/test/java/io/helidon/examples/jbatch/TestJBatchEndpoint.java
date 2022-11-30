/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.examples.jbatch;

import io.helidon.microprofile.tests.junit5.HelidonTest;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;

import java.util.Collections;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
public class TestJBatchEndpoint {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    @Inject
    private WebTarget webTarget;

    @Test
    public void runJob() throws InterruptedException {

        JsonObject expectedJson = JSON.createObjectBuilder()
                .add("Steps executed", "[step1, step2]")
                .add("Status", "COMPLETED")
                .build();

        //Start the job
        JsonObject jsonObject = webTarget
                .path("/batch")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(JsonObject.class);

        Integer responseJobId = jsonObject.getInt("Started a job with Execution ID: ");
        assertThat(responseJobId, is(notNullValue()));
        JsonObject result = null;
        for (int i = 1; i < 10; i++) {
            //Wait a bit for it to complete
            Thread.sleep(i*1000);

            //Examine the results
            result = webTarget
                    .path("batch/status/" + responseJobId)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get(JsonObject.class);

            if (result.equals(expectedJson)){
                break;
            }

        }

        assertThat(result, equalTo(expectedJson));
    }
}
