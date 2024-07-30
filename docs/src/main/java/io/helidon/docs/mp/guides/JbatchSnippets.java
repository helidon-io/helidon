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
package io.helidon.docs.mp.guides;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;

import io.helidon.common.configurable.ThreadPoolSupplier;

import com.ibm.jbatch.spi.BatchSPIManager;
import com.ibm.jbatch.spi.ExecutorServiceProvider;
import jakarta.batch.api.AbstractBatchlet;
import jakarta.batch.api.chunk.AbstractItemReader;
import jakarta.batch.api.chunk.AbstractItemWriter;
import jakarta.batch.api.chunk.ItemProcessor;
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

@SuppressWarnings("ALL")
class JbatchSnippets {

    // tag::snippet_1[]
    public class MyInputRecord {
        private int id;

        public MyInputRecord(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "MyInputRecord: " + id;
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    public class MyOutputRecord {

        private int id;

        public MyOutputRecord(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "MyOutputRecord: " + id;
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    public class MyItemReader extends AbstractItemReader {

        private final StringTokenizer tokens;

        public MyItemReader() {
            tokens = new StringTokenizer("1,2,3,4,5,6,7,8,9,10", ",");
        }

        /**
         * Perform read Item.
         *
         * @return Stage result.
         */
        @Override
        public MyInputRecord readItem() {
            if (tokens.hasMoreTokens()) {
                return new MyInputRecord(Integer.valueOf(tokens.nextToken()));
            }
            return null;
        }
    }
    // end::snippet_3[]

    // tag::snippet_4[]
    public class MyItemProcessor implements ItemProcessor {

        @Override
        public MyOutputRecord processItem(Object t) {
            System.out.println("processItem: " + t);

            return (((MyInputRecord) t).getId() % 2 == 0) ? null : new MyOutputRecord(((MyInputRecord) t).getId() * 2);
        }
    }
    // end::snippet_4[]

    // tag::snippet_5[]
    public class MyItemWriter extends AbstractItemWriter {

        @Override
        public void writeItems(List list) {
            System.out.println("writeItems: " + list);
        }
    }
    // end::snippet_5[]

    // tag::snippet_6[]
    public class MyBatchlet extends AbstractBatchlet {

        @Override
        public String process() {
            System.out.println("Running inside a batchlet");

            return "COMPLETED";
        }

    }
    // end::snippet_6[]

    // tag::snippet_7[]
    @Path("/batch")
    @ApplicationScoped
    public class BatchResource {
        private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

        private JobOperator jobOperator;

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

        @GET
        @Path("/status/{execution-id}")
        public JsonObject status(@PathParam("execution-id") Long executionId) {
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
    // end::snippet_7[]

    // tag::snippet_8[]
    public class HelidonExecutorServiceProvider implements ExecutorServiceProvider {
        @Override
        public ExecutorService getExecutorService() {
            return ThreadPoolSupplier.builder().corePoolSize(2).build().get();
        }
    }
    // end::snippet_8[]

}
