/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.metrics;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Metrics;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import com.oracle.bmc.monitoring.Monitoring;
import com.oracle.bmc.monitoring.model.PostMetricDataDetails;
import com.oracle.bmc.monitoring.requests.PostMetricDataRequest;
import com.oracle.bmc.monitoring.responses.PostMetricDataResponse;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testing.Test
class OciMetricsPublisherConfigTest {

    @Test
    void testPublisherYaml() throws InterruptedException {
        Monitoring monitoring = mock(Monitoring.class);
        var transmissions = new LinkedBlockingQueue<PostMetricDataDetails>();
        when(monitoring.getEndpoint()).thenReturn("https://telemetry.example.com");
        when(monitoring.postMetricData(any())).thenAnswer(invocation -> {
            PostMetricDataRequest request = invocation.getArgument(0);
            transmissions.add(PostMetricDataDetails.builder()
                                      .metricData(List.copyOf(request.getPostMetricDataDetails().getMetricData()))
                                      .build());
            return PostMetricDataResponse.builder().__httpStatusCode__(200).build();
        });
        Services.set(Monitoring.class, monitoring);

        var registry = Metrics.globalRegistry();
        registry.getOrCreate(Counter.builder("configuredCounterOne").scope(Meter.Scope.APPLICATION)).increment(3);
        registry.getOrCreate(Counter.builder("configuredCounterTwo").scope(Meter.Scope.APPLICATION)).increment(7);
        registry.getOrCreate(Counter.builder("excludedCounter").scope(Meter.Scope.BASE)).increment();

        Config config = Config.just("""
                metrics:
                  publishers:
                    - type: oci
                      compartment-id: test-compartment
                      namespace: test-namespace
                      resource-group: test-resource-group
                      initial-delay: PT0.05S
                      delay: PT0.1S
                      batch-delay: PT0.025S
                      batch-size: 1
                      scopes: [application]
                """, MediaTypes.APPLICATION_YAML);
        Config publisherConfig = config.get("metrics.publishers").asNodeList().get().getFirst();
        var provider = new OciMetricsPublisherProvider();
        assertThat(publisherConfig.get("type").asString().get(), is(provider.configKey()));

        // Use the standard provider directly; the extension test registers a higher-weight provider for the same type.
        try (var publisher = (OciMetricsService) provider.create(publisherConfig, "oci")) {
            publisher.startPublishing();
            assertThat(publisher.prototype().initialDelay(), is(Duration.ofMillis(50)));
            assertThat(publisher.prototype().delay(), is(Duration.ofMillis(100)));
            assertThat(publisher.prototype().batchDelay(), is(Duration.ofMillis(25)));

            var firstTransmission = transmissions.poll(10, TimeUnit.SECONDS);
            var secondTransmission = transmissions.poll(10, TimeUnit.SECONDS);
            assertThat(firstTransmission, is(notNullValue()));
            assertThat(secondTransmission, is(notNullValue()));
            assertThat(firstTransmission.getMetricData(), hasSize(1));
            assertThat(secondTransmission.getMetricData(), hasSize(1));

            var firstMetric = firstTransmission.getMetricData().getFirst();
            var secondMetric = secondTransmission.getMetricData().getFirst();
            for (var metric : List.of(firstMetric, secondMetric)) {
                assertThat(metric.getCompartmentId(), is("test-compartment"));
                assertThat(metric.getNamespace(), is("test-namespace"));
                assertThat(metric.getResourceGroup(), is("test-resource-group"));
                assertThat(metric.getDimensions().get("scope"), is(Meter.Scope.APPLICATION));
            }
            assertThat(List.of(firstMetric.getName(), secondMetric.getName()),
                       containsInAnyOrder("configuredCounterOne_counter", "configuredCounterTwo_counter"));
            assertThat(List.of(firstMetric.getDatapoints().getFirst().getValue(),
                               secondMetric.getDatapoints().getFirst().getValue()),
                       containsInAnyOrder(3.0, 7.0));
        } finally {
            registry.meters().forEach(registry::remove);
        }
    }
}
