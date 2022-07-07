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
 */
package io.helidon.microprofile.grpc.metrics;

import java.util.HashMap;
import java.util.Map;

import io.helidon.microprofile.metrics.MetricAnnotationDiscoveryObserver.MetricAnnotationDiscovery;
import io.helidon.microprofile.metrics.MetricRegistrationObserver;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;

/**
 * The gRPC implementation of {@link io.helidon.microprofile.metrics.MetricRegistrationObserver} with a static factory method.
 */
class MetricRegistrationObserverImpl implements MetricRegistrationObserver {


    private final Map<MetricAnnotationDiscovery, Metadata> metadataByDiscovery = new HashMap<>();

    public MetricRegistrationObserverImpl() {
        int i = 4;
    }

    @Override
    public void onRegistration(MetricAnnotationDiscovery discovery,
                               Metadata metadata,
                               MetricID metricId,
                               Metric metric) {
        metadataByDiscovery.put(discovery, metadata);
    }

    Metadata metadata(MetricAnnotationDiscovery discovery) {
        return metadataByDiscovery.get(discovery);
    }

    static MetricRegistrationObserverImpl instance() {
        return MetricRegistrationObserverImplFactory.instance();
    }
}
