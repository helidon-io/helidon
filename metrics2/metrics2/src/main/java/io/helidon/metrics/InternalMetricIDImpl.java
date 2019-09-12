/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package io.helidon.metrics;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.helidon.common.metrics.InternalBridge;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Tag;

/**
 *
 */
class InternalMetricIDImpl implements InternalBridge.MetricID {

    private final MetricID delegate;

    InternalMetricIDImpl(String name) {
        delegate = new org.eclipse.microprofile.metrics.MetricID(name);
    }

    InternalMetricIDImpl(String name, Map<String, String> tags) {
        delegate = new org.eclipse.microprofile.metrics.MetricID(name, toTagArray(tags));
    }

    private Tag[] toTagArray(Map<String, String> tags) {
        return tags.entrySet().stream()
                .map(entry -> new Tag(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList())
                .toArray(new Tag[0]);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Map<String, String> getTags() {
        return delegate.getTags();
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 37 * hash + Objects.hashCode(this.delegate);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final InternalMetricIDImpl other = (InternalMetricIDImpl) obj;
        return Objects.equals(this.delegate, other.delegate);
    }

    static class FactoryImpl implements Factory {

        @Override
        public io.helidon.common.metrics.InternalBridge.MetricID newMetricID(String name) {
            return new InternalMetricIDImpl(name);
        }

        @Override
        public io.helidon.common.metrics.InternalBridge.MetricID newMetricID(String name, Map<String, String> tags) {
            return new InternalMetricIDImpl(name, tags);
        }
    }
}
