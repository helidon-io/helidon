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

import io.helidon.common.LazyValue;
import io.helidon.microprofile.metrics.spi.MetricRegistrationObserver;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;

public class TestRegistrationObserverImpl implements MetricRegistrationObserver {

    private static final LazyValue<TestRegistrationObserverImpl> INSTANCE = LazyValue.create(TestRegistrationObserverImpl::new);

    static TestRegistrationObserverImpl instance() {
        return INSTANCE.get();
    }

    private int registrations;

    @Override
    public void onRegistration(MetricAnnotationDiscovery discovery,
                               Metadata metadata,
                               MetricID metricId,
                               Metric metric) {
        registrations++;
    }

    static int registrations() {
        return INSTANCE.get().registrations;
    }
}
