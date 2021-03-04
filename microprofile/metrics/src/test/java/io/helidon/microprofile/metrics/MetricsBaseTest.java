/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.metrics;

import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;

import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.servicecommon.restcdi.HelidonRestCdiExtension;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;

/**
 * Class MetricsBaseTest.
 */
@HelidonTest
public class MetricsBaseTest {

    private static final String METRIC_NAME_TEMPLATE = "%s.%s";

    @Inject
    private MetricRegistry metricRegistry;

    MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    @SuppressWarnings("unchecked")
    <T extends Metric> T getMetric(Object bean, String name) {
        MetricID metricName = new MetricID(String.format(METRIC_NAME_TEMPLATE,
                HelidonRestCdiExtension.realClass(bean).getName(),        // CDI proxies
                name));
        return (T) getMetricRegistry().getMetrics().get(metricName);
    }

    <T> T newBean(Class<T> beanClass) {
        return CDI.current().select(beanClass).get();
    }
}
