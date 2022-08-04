/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.metrics;

import io.helidon.microprofile.metrics.MetricsCdiExtension;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;

/**
 * A CDI extension for gRPC metrics.
 * <p>
 * This extension instantiates and enrolls with the metrics CDI extension a metrics annotation discovery observer and a metrics
 * registration observer. It records them for later use by the {@code MetricsConfigurer}.
 */
public class GrpcMetricsCdiExtension implements Extension {

    private void before(@Observes BeforeBeanDiscovery bbd, BeanManager beanManager) {
        MetricsCdiExtension metricsCdiExtension = beanManager.getExtension(MetricsCdiExtension.class);
        metricsCdiExtension.enroll(GrpcMetricAnnotationDiscoveryObserver.instance());
        metricsCdiExtension.enroll(GrpcMetricRegistrationObserver.instance());
    }
}
