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
package io.helidon.jbatch.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import com.ibm.jbatch.spi.BatchSPIManager;
import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.JobExecution;
import jakarta.batch.runtime.StepExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;


import static jakarta.batch.runtime.BatchRuntime.getJobOperator;


/**
 * Trigger a batch process using resource.
 */
@Path("/batch")
@ApplicationScoped
public class BatchResource {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private JobOperator jobOperator;

    /**
     * Run a JBatch process when endpoint called.
     * @return JsonObject with the result.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject executeBatch() {

        BatchSPIManager batchSPIManager = BatchSPIManager.getInstance();
        batchSPIManager.registerPlatformMode(BatchSPIManager.PlatformMode.SE);
        batchSPIManager.registerExecutorServiceProvider(new HelidonExecutorServiceProvider());

        jobOperator = getJobOperator();
        Long executionId = jobOperator.start("myJob", new Properties());

        return JSON.createObjectBuilder()
                .add("Started a job with Execution ID: ", executionId)
                .build();
    }

    /**
     * Check the job status.
     * @param executionId the job ID.
     * @return JsonObject with status.
     */
    @GET
    @Path("/status/{execution-id}")
    public JsonObject status(@PathParam("execution-id") Long executionId){
        JobExecution jobExecution = jobOperator.getJobExecution(executionId);

        List<StepExecution> stepExecutions = jobOperator.getStepExecutions(executionId);
        List<String> executedSteps = new ArrayList<>();
        for (StepExecution stepExecution : stepExecutions) {
            executedSteps.add(stepExecution.getStepName());
        }

        return JSON.createObjectBuilder()
                .add("Steps executed", Arrays.toString(executedSteps.toArray()))
                .add("Status", jobExecution.getBatchStatus().toString())
                .build();
    }
}
