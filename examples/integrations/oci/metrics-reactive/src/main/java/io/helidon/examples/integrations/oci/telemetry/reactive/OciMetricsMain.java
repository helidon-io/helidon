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

package io.helidon.examples.integrations.oci.telemetry.reactive;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import io.helidon.common.LogConfig;
import io.helidon.config.Config;
import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.oci.telemetry.OciMetricsRx;
import io.helidon.integrations.oci.telemetry.PostMetricData;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

/**
 * OCI Metrics example.
 */
public class OciMetricsMain {
    /**
     * Main method.
     * @param args ignored
     */
    public static void main(String[] args) {
        LogConfig.configureRuntime();
        // as I cannot share my configuration of OCI, let's combine the configuration
        // from my home directory with the one compiled into the jar
        // when running this example, you can either update the application.yaml in resources directory
        // or use the same approach
        Config config = buildConfig();

        OciMetricsRx metrics = OciMetricsRx.create(config.get("oci"));

        String compartmentId = config.get("oci.metrics.compartment-ocid").asString().get();

        Instant now = Instant.now();
        PostMetricData.Request request = PostMetricData.Request.builder()
                .addMetricData(PostMetricData.MetricData.builder()
                                       .compartmentId(compartmentId)
                                       // Add a few data points to see something in the console
                                       .addDataPoint(now.minus(10, ChronoUnit.SECONDS), 101)
                                       .addDataPoint(now.minus(5, ChronoUnit.SECONDS), 123)
                                       .addDataPoint(now, 149)
                                       .addDimension("resourceId", "myresourceid")
                                       .addMetaData("unit", "cm")
                                       .name("my_app.jump")
                                       .namespace("helidon_examples"));

        /*
         * Invoke the API call. I use .await() to block the call, as otherwise our
         * main method would finish without waiting for the response.
         * In a real reactive application, this should not be done (as you would write the response
         * to a server response or use other reactive/non-blocking APIs).
         */
        metrics.postMetricData(request)
                .peek(it -> {
                    System.out.println("PostMetrics: " + it.status());
                    printHeaders(it);
                })
                .forSingle(it -> {
                    int count = it.failedMetricsCount();
                    System.out.println("Failed count: " + count);
                    if (count > 0) {
                        System.out.println("Failed metrics:");
                        for (PostMetricData.FailedMetric failedMetric : it.failedMetrics()) {
                            System.out.println("\t" + failedMetric.message() + ": " + failedMetric.failedDataJson());
                        }
                    }
                })
                .await();
    }

    private static void printHeaders(ApiEntityResponse response) {
        System.out.println("\tHeaders:");
        response.headers().toMap().forEach((key, values) -> System.out.println("\t\t" + key + ": " + values));
    }

    private static Config buildConfig() {
        return Config.builder()
                .sources(
                        // you can use this file to override the defaults that are built-in
                        file(System.getProperty("user.home") + "/helidon/conf/examples.yaml").optional(),
                        // in jar file (see src/main/resources/application.yaml)
                        classpath("application.yaml"))
                .build();
    }
}
