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

import java.util.Objects;

import java.util.Collections;
import java.util.Map;

import io.helidon.common.metrics.InternalBridge;
import io.helidon.common.metrics.InternalBridge.Metadata;

import org.eclipse.microprofile.metrics.MetricType;

/**
 *
 */
class InternalMetadataBuilderImpl implements InternalBridge.Metadata.MetadataBuilder {

    private String name;
    private String displayName;
    private String description;
    private MetricType type;
    private String unit;
    private boolean reusable;
    private Map<String, String> tags;

    public InternalMetadataBuilderImpl() {
    }

    InternalMetadataBuilderImpl(Metadata metadata) {
        this.name = metadata.getName();
        this.type = metadata.getTypeRaw();
        this.reusable = metadata.isReusable();
        this.displayName = metadata.getDisplayName();
        metadata.getDescription().ifPresent(this::withDescription);
        metadata.getUnit().ifPresent(this::withUnit);
    }

    public InternalMetadataBuilderImpl withName(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        return this;
    }

    public InternalMetadataBuilderImpl withDisplayName(String displayName) {
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
        return this;
    }

    public InternalMetadataBuilderImpl withDescription(String description) {
        this.description = Objects.requireNonNull(description, "description cannot be null");
        return this;
    }

    public InternalMetadataBuilderImpl withType(MetricType type) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        return this;
    }

    public InternalMetadataBuilderImpl withUnit(String unit) {
        this.unit = Objects.requireNonNull(unit, "unit cannot be null");
        return this;
    }

    @Override
    public Metadata.MetadataBuilder withTags(Map<String, String> tags) {
        this.tags = Collections.unmodifiableMap(tags);
        return this;
    }

    public InternalMetadataBuilderImpl reusable() {
        this.reusable = true;
        return this;
    }

    public InternalMetadataBuilderImpl notReusable() {
        this.reusable = false;
        return this;
    }

    public boolean isReusable() {
        return reusable;
    }

    public Metadata build() {
        if (name == null) {
            throw new IllegalStateException("name must be assigned");
        }
        return new InternalMetadataImpl(name, displayName, description, type, unit);
    }

    static class FactoryImpl implements Metadata.MetadataBuilder.Factory {

        @Override
        public Metadata.MetadataBuilder newMetadataBuilder() {
            return new InternalMetadataBuilderImpl();
        }
    }
}
