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
package io.helidon.integrations.oci.metrics.cdi;

import java.lang.reflect.Method;
import java.util.Arrays;

import io.helidon.config.Config;
import io.helidon.microprofile.server.ServerCdiExtension;

import com.oracle.bmc.monitoring.Monitoring;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.ws.rs.Priorities;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

class TestObserverPriorities {

    @Test
    void compareObserverAgainstMetricsCdiExtensionObserver() throws NoSuchMethodException, ClassNotFoundException {
        Method ourObserverMethod = OciMetricsBean.class.getDeclaredMethod("registerOciMetrics",
                                                                          Object.class,
                                                                          Config.class,
                                                                          Monitoring.class);
        Class<?> metricsCdiExtension = Class.forName("io.helidon.microprofile.metrics.MetricsCdiExtension");
        Method metricsCdiExtensionRegisteringMethod = metricsCdiExtension.getDeclaredMethod("registerService",
                                                                                                  Object.class,
                                                                                                  BeanManager.class,
                                                                                                  ServerCdiExtension.class);
        assertThat("Observer priority compared to metrics CDI extension observer priority",
                   priority(ourObserverMethod),
                   is(greaterThan(priority(metricsCdiExtensionRegisteringMethod))));
    }

    private static int priority(Method method) {
        return Arrays.stream(method.getParameters())
                .filter(p -> p.isAnnotationPresent(Observes.class))
                .filter(p -> p.isAnnotationPresent(Priority.class))
                .map(p -> p.getAnnotation(Priority.class).value())
                .findFirst()
                .orElse(Priorities.USER);

    }
}
