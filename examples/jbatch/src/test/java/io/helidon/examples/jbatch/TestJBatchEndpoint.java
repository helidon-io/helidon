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

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


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

        boolean result = false;
        for (int i = 1; i < 10; i++) {
            //Wait a bit for it to complete
            Thread.sleep(i*1000);

            //Examine the results
            jsonObject = webTarget
                    .path("batch/status/" + responseJobId)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get(JsonObject.class);

            String responseString = jsonObject.toString();
            result = responseString.equals("{\"Steps executed\":\"[step1, step2]\",\"Status\":\"COMPLETED\"}");

            if (result) break;
        }

        assertTrue(result, "Job Result string");
    }
}
