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
 *
 */
package io.helidon.examples.jbatch;

import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@HelidonTest
public class TestJBatchEndpoint {

    @Inject
    private WebTarget webTarget;

    @Test
    public void runJob() throws InterruptedException {

        //Start the job
        JsonObject jsonObject = webTarget
                .path("/batch")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(JsonObject.class);

        Integer responseJobId = jsonObject.getInt("Started a job with Execution ID: ");
        assertNotNull(responseJobId, "Response Job Id");

        //Wait a bit for it to complete
        Thread.sleep(1000);

        //Examine the results
        jsonObject = webTarget
                .path("batch/status/" + responseJobId)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(JsonObject.class);

        String responseString = jsonObject.toString();

        assertEquals("{\"Steps executed\":\"[step1, step2]\",\"Status\":\"COMPLETED\"}",
                responseString, "Job Result string");
    }
}
