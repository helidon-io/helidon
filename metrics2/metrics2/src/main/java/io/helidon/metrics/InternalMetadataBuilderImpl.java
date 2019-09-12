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

import io.helidon.common.metrics.InternalBridge;
import io.helidon.common.metrics.InternalBridge.Metadata;
import io.helidon.common.metrics.InternalBridge.Metadata.MetadataBuilder;

import org.eclipse.microprofile.metrics.MetricType;

/**
 * Fluent-style builder for version-neutral {@link Metadata}.
 *
 */
class InternalMetadataBuilderImpl implements MetadataBuilder {

    private final org.eclipse.microprofile.metrics.MetadataBuilder delegate;

    InternalMetadataBuilderImpl() {
        delegate = new org.eclipse.microprofile.metrics.MetadataBuilder();
    }

    @Override
    public MetadataBuilder withName(String name) {
        delegate.withName(name);
        return this;
    }

    @Override
    public MetadataBuilder withDisplayName(String displayName) {
        delegate.withDisplayName(displayName);
        return this;
    }

    @Override
    public MetadataBuilder withDescription(String description) {
        delegate.withDescription(description);
        return this;
    }

    @Override
    public MetadataBuilder withType(MetricType type) {
        delegate.withType(type);
        return this;
    }

    @Override
    public MetadataBuilder withUnit(String unit) {
        delegate.withUnit(unit);
        return this;
    }

    @Override
    public MetadataBuilder reusable() {
        delegate.reusable();
        return this;
    }

    @Override
    public MetadataBuilder notReusable() {
        delegate.notReusable();
        return this;
    }

    @Override
    public Metadata.MetadataBuilder withTags(Map<String, String> tags) {
        return this;
    }

    @Override
    public InternalBridge.Metadata build() {
        return new InternalMetadataImpl(delegate.build());
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return (obj != null)
                && (this.getClass().isAssignableFrom(obj.getClass()))
                && delegate.equals(((InternalMetadataBuilderImpl) obj).delegate);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    static class FactoryImpl implements Factory {

        @Override
        public Metadata.MetadataBuilder newMetadataBuilder() {
            return new InternalMetadataBuilderImpl();
        }
    }
}
