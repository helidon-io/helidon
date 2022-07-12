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

import io.helidon.common.LazyValue;
import io.helidon.microprofile.metrics.spi.MetricAnnotationDiscoveryObserver;

public class TestDiscoveryObserverImpl implements MetricAnnotationDiscoveryObserver {

    private static final LazyValue<TestDiscoveryObserverImpl> INSTANCE = LazyValue.create(TestDiscoveryObserverImpl::new);

    private final List<MetricAnnotationDiscovery> discoveries = new ArrayList<>();

    static TestDiscoveryObserverImpl instance() {
        return INSTANCE.get();
    }

    @Override
    public void onDiscovery(MetricAnnotationDiscovery metricAnnotationDiscovery) {
        discoveries.add(metricAnnotationDiscovery);
    }

    static List<MetricAnnotationDiscovery> discoveries() {
        return instance().discoveries;
    }
}
