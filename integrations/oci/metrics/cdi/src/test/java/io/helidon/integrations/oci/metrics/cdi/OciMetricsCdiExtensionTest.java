/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.integrations.oci.metrics.OciMetricsSupport;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import com.oracle.bmc.monitoring.Monitoring;
import com.oracle.bmc.monitoring.model.MetricDataDetails;
import com.oracle.bmc.monitoring.model.PostMetricDataDetails;
import com.oracle.bmc.monitoring.requests.PostMetricDataRequest;
import com.oracle.bmc.monitoring.responses.PostMetricDataResponse;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.configurator.BeanConfigurator;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

@HelidonTest(resetPerTest = true)
@DisableDiscovery
@AddBean(OciMetricsCdiExtensionTest.MockOciMetricsBean.class)
// Helidon MP Extensions
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(ConfigCdiExtension.class)
@AddExtension(CdiComponentProvider.class)
// Add an extension that will simulate a mocked OciExtension that will inject a mocked Monitoring object
@AddExtension(OciMetricsCdiExtensionTest.MockOciMonitoringExtension.class)
// ConfigSources
@AddConfig(key = "ocimetrics.compartmentId",
           value = OciMetricsCdiExtensionTest.MetricDataDetailsOCIParams.compartmentId)
@AddConfig(key = "ocimetrics.namespace",
           value = OciMetricsCdiExtensionTest.MetricDataDetailsOCIParams.namespace)
@AddConfig(key = "ocimetrics.resourceGroup",
           value = OciMetricsCdiExtensionTest.MetricDataDetailsOCIParams.resourceGroup)
@AddConfig(key = "ocimetrics.initialDelay", value = "0")
@AddConfig(key = "ocimetrics.delay", value = "1")
class OciMetricsCdiExtensionTest {
    private static String METRIC_NAME_SUFFIX = "DummyCounter";
    private static volatile int testMetricCount = 0;
    private static CountDownLatch countDownLatch = new CountDownLatch(1);
    private static PostMetricDataDetails postMetricDataDetails;
    private static boolean activateOciMetricsSupportIsInvoked;
    private static MeterRegistry registry = Metrics.globalRegistry();

    @AfterEach
    void resetState() {
        postMetricDataDetails = null;
        activateOciMetricsSupportIsInvoked = false;
        countDownLatch = new CountDownLatch(1);
    }

    @Test
    @AddConfig(key = "ocimetrics.enabled", value = "true")
    void testEnableOciMetrics() throws InterruptedException {
        validateOciMetricsSupport(true);
    }

    @Test
    void testEnableOciMetricsWithoutConfig() throws InterruptedException {
        validateOciMetricsSupport(true);
    }

    @Test
    @AddConfig(key = "ocimetrics.enabled", value = "false")
    void testDisableOciMetrics() throws InterruptedException {
        validateOciMetricsSupport(false);
    }

    private void validateOciMetricsSupport(boolean enabled) throws InterruptedException {
        Counter c1 = registry.getOrCreate(Counter.builder(Meter.Scope.BASE + METRIC_NAME_SUFFIX)
                                     .scope(Meter.Scope.BASE));
        c1.increment();
        Counter c2 = registry.getOrCreate(Counter.builder(Meter.Scope.VENDOR + METRIC_NAME_SUFFIX)
                                     .scope(Meter.Scope.VENDOR));
        c2.increment();
        Counter c3 = registry.getOrCreate(Counter.builder(Meter.Scope.APPLICATION + METRIC_NAME_SUFFIX)
                                     .scope(Meter.Scope.APPLICATION));
        c3.increment();

        // Wait for signal from metric update that testMetricCount has been retrieved
        if (!countDownLatch.await(3, TimeUnit.SECONDS)) {
            // If Oci Metrics is enabled, this means that countdown() of CountDownLatch was never triggered, and hence should fail
            if (enabled) {
                fail("CountDownLatch timed out");
            }
        }

        if (enabled) {
            assertThat(activateOciMetricsSupportIsInvoked, is(true));
            // System meters in the registry might vary over time. Instead of looking for a specific number of meters,
            // make sure the three we added are in the OCI metric data.
            assertThat(testMetricCount, is(3));

            MetricDataDetails metricDataDetails = postMetricDataDetails.getMetricData().getFirst();
            assertThat(metricDataDetails.getCompartmentId(),
                       is(MetricDataDetailsOCIParams.compartmentId));
            assertThat(metricDataDetails.getNamespace(), is(MetricDataDetailsOCIParams.namespace));
            assertThat(metricDataDetails.getResourceGroup(), is(MetricDataDetailsOCIParams.resourceGroup));
        } else {
            assertThat(activateOciMetricsSupportIsInvoked, is(false));
            assertThat(testMetricCount, is(0));
            // validate that OCI post metric is never called
            assertThat(postMetricDataDetails, is(equalTo(null)));
        }
        registry.remove(c1);
        registry.remove(c2);
        registry.remove(c3);
    }

    interface MetricDataDetailsOCIParams {
        String compartmentId = "dummy.compartmentId";
        String namespace = "dummy-namespace";
        String resourceGroup = "dummy_resourceGroup";
    }

    // Use this to replace OciExtension, but will only process OCI Monitoring annotation. If the Monitoring
    // annotation is found, a Mocked Monitoring object will be injected as a bean
    static public class MockOciMonitoringExtension implements Extension {
        boolean monitoringFound;
        Set<Annotation> monitoringQualifiers;

        void processInjectionPoint(@Observes final ProcessInjectionPoint<?, ?> event) {
            if (event != null) {
                InjectionPoint ip = event.getInjectionPoint();
                Class<?> c = (Class<?>) ip.getAnnotated().getBaseType();
                final Set<Annotation> existingQualifiers = ip.getQualifiers();
                if (c == Monitoring.class && existingQualifiers != null && !existingQualifiers.isEmpty()) {
                    monitoringFound = true;
                    monitoringQualifiers = existingQualifiers;
                }
            }
        }

        Monitoring getMockedMonitoring() {
            // Use Proxy to mock only getEndPoint() and postMetricDataDetails() methods of the Monitoring interface,
            // as those are the only ones needed by the test
            return
                    (Monitoring) Proxy.newProxyInstance(
                            Monitoring.class.getClassLoader(),
                            new Class[] {Monitoring.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("getEndpoint")) {
                                    return "http://www.DummyEndpoint.com";
                                } else if (method.getName().equals("postMetricData")) {
                                    // startupMetricLatch.await(5, TimeUnit.SECONDS);
                                    PostMetricDataRequest postMetricDataRequest = (PostMetricDataRequest) args[0];
                                    postMetricDataDetails = postMetricDataRequest.getPostMetricDataDetails();
                                    testMetricCount = (int) postMetricDataDetails.getMetricData().stream()
                                            .filter(details -> details.getName().contains(METRIC_NAME_SUFFIX))
                                            .count();
                                    PostMetricDataResponse response = PostMetricDataResponse.builder()
                                            .__httpStatusCode__(200)
                                            .build();

                                    // Give signal that metrics will be posted if the right no. of metrics has been retrieved.
                                    // If not, that means that the metrics have not been registered yet, so try again on the
                                    // next invocation.
                                    if (testMetricCount > 0) {
                                        countDownLatch.countDown();
                                    }
                                    return response;
                                }
                                return null;
                            });
        }

        void afterBeanDiscovery(@Observes AfterBeanDiscovery event) {
            if (monitoringFound) {
                BeanConfigurator<Object> beanConfigurator = event.addBean()
                        .types(Monitoring.class)
                        .scope(ApplicationScoped.class)
                        .addQualifiers(monitoringQualifiers);
                beanConfigurator = monitoringQualifiers != null ? beanConfigurator.addQualifiers(monitoringQualifiers) :
                        beanConfigurator.addQualifier(Default.Literal.INSTANCE);
                // Add the mocked Monitoring as a bean
                beanConfigurator.produceWith(obj -> getMockedMonitoring());
            } else {
                throw new IllegalStateException("Monitoring was never injected. Check if OciMetricsBean.registerOciMetrics() "
                                                            + "has changed and does not inject Monitoring anymore.");
            }
        }
    }

    static class MockOciMetricsBean extends OciMetricsBean {
        // Override so we can test if this is invoked when enabled or skipped when disabled
        @Override
        protected void activateOciMetricsSupport(Config rootConfig, Config ociMetricsConfig, OciMetricsSupport.Builder builder) {
            activateOciMetricsSupportIsInvoked = true;
            super.activateOciMetricsSupport(rootConfig, ociMetricsConfig, builder);
        }
    }
}
