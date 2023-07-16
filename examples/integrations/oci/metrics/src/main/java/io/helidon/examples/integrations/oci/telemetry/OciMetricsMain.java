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

package io.helidon.examples.integrations.oci.telemetry;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.monitoring.Monitoring;
import com.oracle.bmc.monitoring.MonitoringClient;
import com.oracle.bmc.monitoring.model.Datapoint;
import com.oracle.bmc.monitoring.model.FailedMetricRecord;
import com.oracle.bmc.monitoring.model.MetricDataDetails;
import com.oracle.bmc.monitoring.model.PostMetricDataDetails;
import com.oracle.bmc.monitoring.model.PostMetricDataResponseDetails;
import com.oracle.bmc.monitoring.requests.PostMetricDataRequest;
import com.oracle.bmc.monitoring.responses.PostMetricDataResponse;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

/**
 * OCI Metrics example.
 */
public final class OciMetricsMain {

    private OciMetricsMain() {
    }

    /**
     * Main method.
     *
     * @param args ignored
     */
    public static void main(String[] args) throws Exception {
        LogConfig.configureRuntime();
        // as I cannot share my configuration of OCI, let's combine the configuration
        // from my home directory with the one compiled into the jar
        // when running this example, you can either update the application.yaml in resources directory
        // or use the same approach
        Config config = buildConfig();

        // this requires OCI configuration in the usual place
        // ~/.oci/config
        AuthenticationDetailsProvider authProvider = new ConfigFileAuthenticationDetailsProvider(ConfigFileReader.parseDefault());
        try (Monitoring monitoringClient = MonitoringClient.builder().build(authProvider)) {
            monitoringClient.setEndpoint(monitoringClient.getEndpoint().replace("telemetry.", "telemetry-ingestion."));

            PostMetricDataRequest postMetricDataRequest = PostMetricDataRequest.builder()
                    .postMetricDataDetails(getPostMetricDataDetails(config))
                    .build();

            // Invoke the API call.
            PostMetricDataResponse postMetricDataResponse = monitoringClient.postMetricData(postMetricDataRequest);
            PostMetricDataResponseDetails postMetricDataResponseDetails = postMetricDataResponse
                    .getPostMetricDataResponseDetails();
            int count = postMetricDataResponseDetails.getFailedMetricsCount();
            System.out.println("Failed count: " + count);
            if (count > 0) {
                System.out.println("Failed metrics:");
                for (FailedMetricRecord failedMetric : postMetricDataResponseDetails.getFailedMetrics()) {
                    System.out.println("\t" + failedMetric.getMessage() + ": " + failedMetric.getMetricData());
                }
            }
        }
    }

    private static PostMetricDataDetails getPostMetricDataDetails(Config config) {
        String compartmentId = config.get("oci.metrics.compartment-ocid").asString().get();
        Instant now = Instant.now();
        return PostMetricDataDetails.builder()
                .metricData(List.of(
                        MetricDataDetails.builder()
                                .compartmentId(compartmentId)
                                // Add a few data points to see something in the console
                                .datapoints(List.of(
                                        Datapoint.builder()
                                                .timestamp(Date.from(now.minus(10, ChronoUnit.SECONDS)))
                                                .value(101.00)
                                                .build(),
                                        Datapoint.builder()
                                                .timestamp(Date.from(now))
                                                .value(149.00)
                                                .build()
                                ))
                                .dimensions(Map.of(
                                        "resourceId", "myresourceid",
                                        "unit", "cm"))
                                .name("my_app.jump")
                                .namespace("helidon_examples")
                                .build()
                ))
                .batchAtomicity(PostMetricDataDetails.BatchAtomicity.NonAtomic).build();
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
