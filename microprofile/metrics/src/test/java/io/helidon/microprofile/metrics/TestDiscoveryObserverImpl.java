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
import io.helidon.microprofile.metrics.api.MetricAnnotationDiscoveryObserverProvider;

public class TestDiscoveryObserverImpl implements MetricAnnotationDiscoveryObserver {

    public static class Provider implements MetricAnnotationDiscoveryObserverProvider<TestDiscoveryObserverImpl> {

        private static final LazyValue<TestDiscoveryObserverImpl> instance = LazyValue.create(TestDiscoveryObserverImpl::new);

        @Override
        public TestDiscoveryObserverImpl get() {
            return instance();
        }

        static TestDiscoveryObserverImpl instance() {
            return instance.get();
        }
    }
    private List<MetricAnnotationDiscoveryObserver.MetricAnnotationDiscovery> discoveries = new ArrayList<>();

    @Override
    public void onDiscovery(MetricAnnotationDiscoveryObserver.MetricAnnotationDiscovery metricAnnotationDiscovery) {
        discoveries.add(metricAnnotationDiscovery);
    }

    List<MetricAnnotationDiscoveryObserver.MetricAnnotationDiscovery> discoveries() {
        return discoveries;
    }
}
