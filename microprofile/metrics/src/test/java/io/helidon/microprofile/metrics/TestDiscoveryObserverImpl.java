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
package io.helidon.microprofile.metrics;

import java.util.ArrayList;
import java.util.List;

import io.helidon.microprofile.metrics.spi.MetricAnnotationDiscoveryObserver;

public class TestDiscoveryObserverImpl implements MetricAnnotationDiscoveryObserver {

    private static TestDiscoveryObserverImpl instance;

    static TestDiscoveryObserverImpl instance() {
        return instance;
    }

    private static void instance(TestDiscoveryObserverImpl value) {
        instance = value;
    }

    private List<MetricAnnotationDiscovery> discoveries = new ArrayList<>();

    public TestDiscoveryObserverImpl() {
        instance(this);
    }

    @Override
    public void onDiscovery(MetricAnnotationDiscovery metricAnnotationDiscovery) {
        discoveries.add(metricAnnotationDiscovery);
    }

    List<MetricAnnotationDiscovery> discoveries() {
        return discoveries;
    }
}
