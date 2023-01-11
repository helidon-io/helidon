/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.metrics.cdi;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import static jakarta.interceptor.Interceptor.Priority.APPLICATION;

import com.oracle.bmc.Region;
import com.oracle.bmc.monitoring.Monitoring;
import com.oracle.bmc.monitoring.MonitoringPaginators;
import com.oracle.bmc.monitoring.MonitoringWaiters;
import com.oracle.bmc.monitoring.model.MetricDataDetails;
import com.oracle.bmc.monitoring.model.PostMetricDataDetails;
import com.oracle.bmc.monitoring.requests.*;
import com.oracle.bmc.monitoring.responses.*;

import io.helidon.metrics.api.RegistryFactory;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.metrics.MetricRegistry;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@HelidonTest(resetPerTest = true)
@AddBean(OciMetricsCdiExtensionTest.MockMonitoring.class)
@DisableDiscovery
// Helidon MP Extensions
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
// OciMetricsCdiExtension
@AddExtension(OciMetricsCdiExtension.class)
// ConfigSources
@AddConfig(key = "ocimetrics.compartmentId",
           value = OciMetricsCdiExtensionTest.MetricDataDetailsOCIParams.compartmentId)
@AddConfig(key = "ocimetrics.namespace",
           value = OciMetricsCdiExtensionTest.MetricDataDetailsOCIParams.namespace)
@AddConfig(key = "ocimetrics.resourceGroup",
           value =  OciMetricsCdiExtensionTest.MetricDataDetailsOCIParams.resourceGroup)
@AddConfig(key = "ocimetrics.initialDelay", value = "1")
@AddConfig(key = "ocimetrics.delay", value = "2")
public class OciMetricsCdiExtensionTest {
    private final RegistryFactory rf = RegistryFactory.getInstance();
    private final MetricRegistry baseMetricRegistry = rf.getRegistry(MetricRegistry.Type.BASE);
    private final MetricRegistry vendorMetricRegistry = rf.getRegistry(MetricRegistry.Type.VENDOR);
    private final MetricRegistry appMetricRegistry = rf.getRegistry(MetricRegistry.Type.APPLICATION);
    private static volatile int testMetricCount = 0;
    // Use countDownLatch1 to signal the test that results to be asserted has been retrieved
    private static CountDownLatch countDownLatch1 = new CountDownLatch(1);
    private static PostMetricDataDetails postMetricDataDetails;

    @Test
    public void testRegisterOciMetrics() throws InterruptedException {
        baseMetricRegistry.counter("baseDummyCounter").inc();
        vendorMetricRegistry.counter("vendorDummyCounter").inc();
        appMetricRegistry.counter("appDummyCounter").inc();
        // Wait for signal from metric update that testMetricCount has been retrieved
        countDownLatch1.await(10, TimeUnit.SECONDS);

        assertThat(testMetricCount, is(equalTo(3)));

        MetricDataDetails metricDataDetails = postMetricDataDetails.getMetricData().get(0);
        assertThat(metricDataDetails.getCompartmentId(),
                   is(equalTo(OciMetricsCdiExtensionTest.MetricDataDetailsOCIParams.compartmentId)));
        assertThat(metricDataDetails.getNamespace(), is(equalTo(MetricDataDetailsOCIParams.namespace)));
        assertThat(metricDataDetails.getResourceGroup(), is(equalTo(MetricDataDetailsOCIParams.resourceGroup)));
    }

    @Alternative
    @Priority(APPLICATION + 1)
    static class MockMonitoring implements Monitoring {
        @Override
        public void setEndpoint(String s) {}

        @Override
        public String getEndpoint() {return "http://www.DummyEndpoint.com";}

        @Override
        public void setRegion(Region region) {}

        @Override
        public void setRegion(String s) {}

        @Override
        public void refreshClient() {}

        @Override
        public ChangeAlarmCompartmentResponse changeAlarmCompartment(ChangeAlarmCompartmentRequest changeAlarmCompartmentRequest) {
            return null;
        }

        @Override
        public CreateAlarmResponse createAlarm(CreateAlarmRequest createAlarmRequest) {return null;}

        @Override
        public DeleteAlarmResponse deleteAlarm(DeleteAlarmRequest deleteAlarmRequest) {return null;}

        @Override
        public GetAlarmResponse getAlarm(GetAlarmRequest getAlarmRequest) {return null;}

        @Override
        public GetAlarmHistoryResponse getAlarmHistory(GetAlarmHistoryRequest getAlarmHistoryRequest) {return null;}

        @Override
        public ListAlarmsResponse listAlarms(ListAlarmsRequest listAlarmsRequest) {return null;}

        @Override
        public ListAlarmsStatusResponse listAlarmsStatus(ListAlarmsStatusRequest listAlarmsStatusRequest) {
            return null;
        }

        @Override
        public ListMetricsResponse listMetrics(ListMetricsRequest listMetricsRequest) {return null;}

        @Override
        public PostMetricDataResponse postMetricData(PostMetricDataRequest postMetricDataRequest) {
            postMetricDataDetails = postMetricDataRequest.getPostMetricDataDetails();
            testMetricCount = postMetricDataDetails.getMetricData().size();
            // Give signal that testMetricCount was retrieved
            countDownLatch1.countDown();
            return PostMetricDataResponse.builder()
                    .__httpStatusCode__(200)
                    .build();
        }

        @Override
        public RemoveAlarmSuppressionResponse removeAlarmSuppression(RemoveAlarmSuppressionRequest removeAlarmSuppressionRequest) {
            return null;
        }

        @Override
        public RetrieveDimensionStatesResponse retrieveDimensionStates(RetrieveDimensionStatesRequest retrieveDimensionStatesRequest) {
            return null;
        }

        @Override
        public SummarizeMetricsDataResponse summarizeMetricsData(SummarizeMetricsDataRequest summarizeMetricsDataRequest) {
            return null;
        }

        @Override
        public UpdateAlarmResponse updateAlarm(UpdateAlarmRequest updateAlarmRequest) {return null;}

        @Override
        public MonitoringWaiters getWaiters() {return null;}

        @Override
        public MonitoringPaginators getPaginators() {return null;}

        @Override
        public void close() throws Exception {}
    }

    public interface MetricDataDetailsOCIParams {
        String compartmentId = "dummy.compartmentId";
        String namespace = "dummy-namespace";
        String resourceGroup = "dummy_resourceGroup";
    }

    private static void delay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignore) {
        }
    }
}
