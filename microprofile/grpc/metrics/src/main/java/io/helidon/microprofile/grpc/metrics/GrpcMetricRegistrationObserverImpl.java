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

import io.helidon.microprofile.metrics.MetricAnnotationDiscovery;
import io.helidon.microprofile.metrics.spi.MetricRegistrationObserver;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;

/**
 * The gRPC implementation of {@link io.helidon.microprofile.metrics.spi.MetricRegistrationObserver} with a static factory method.
 */
public class GrpcMetricRegistrationObserverImpl implements MetricRegistrationObserver {

    private static GrpcMetricRegistrationObserverImpl instance;

    static GrpcMetricRegistrationObserverImpl instance() {
        return instance;
    }

    private static void instance(GrpcMetricRegistrationObserverImpl value) {
        instance = value;
    }

    private final Map<MetricAnnotationDiscovery, Metadata> metadataByDiscovery = new HashMap<>();

    /**
     * Creates a new instance of the observer.
     */
    public GrpcMetricRegistrationObserverImpl() {
        instance(this);
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
}
