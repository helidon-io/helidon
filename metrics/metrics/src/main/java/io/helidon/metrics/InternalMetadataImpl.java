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

import java.util.HashMap;
import java.util.Optional;

import io.helidon.common.metrics.InternalBridge.Metadata;

import org.eclipse.microprofile.metrics.MetricType;

/**
 * Metrics 1.1-based implementation of the version-neutral {@code Metadata} interface.
 */
class InternalMetadataImpl implements Metadata {

    private final org.eclipse.microprofile.metrics.Metadata delegate;

    /**
     * Creates a new metadata instance, based on the supplied name, display name,
     * description, type, and unit.
     *
     * @param name name for the metadata
     * @param displayName display name for the metadata
     * @param description description for the metadata
     * @param type metric type for the metadata
     * @param unit unit for the metadata
     */
    InternalMetadataImpl(String name, String displayName, String description,
            MetricType type, String unit) {
        delegate = new org.eclipse.microprofile.metrics.Metadata(
                name, displayName, description, type, unit);
    }

    /**
     * Creates a new metadata instance, based on the supplied name, display name,
     * description, type, and unit.
     *
     * @param name name for the metadata
     * @param displayName display name for the metadata
     * @param description description for the metadata
     * @param type metric type for the metadata
     * @param unit unit for the metadata
     * @param tags tag(s) to be associated with the metadata (name=value format)
     */
    InternalMetadataImpl(String name, String displayName, String description,
            MetricType type, String unit, String tags) {
        delegate = new org.eclipse.microprofile.metrics.Metadata(
                name, displayName, description, type, unit, tags);
    }

    /**
     * Creates a new metadata instance, based on the supplied name, display name,
     * description, type, and unit.
     *
     * @param name name for the metadata
     * @param displayName display name for the metadata
     * @param description description for the metadata
     * @param type metric type for the metadata
     * @param unit unit for the metadata
     * @param isReusable whether corresponding metrics should be reusable or not
     * @param tags tag(s) to be associated with the metadata (name=value format)
     */
    InternalMetadataImpl(String name, String displayName, String description,
            MetricType type, String unit, boolean isReusable, String tags) {
        delegate = new org.eclipse.microprofile.metrics.Metadata(
                name, displayName, description, type, unit, tags);
        delegate.setReusable(isReusable);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public Optional<String> getDescription() {
        return Optional.ofNullable(delegate.getDescription());
    }

    @Override
    public String getType() {
        return delegate.getType();
    }

    @Override
    public MetricType getTypeRaw() {
        return delegate.getTypeRaw();
    }

    @Override
    public Optional<String> getUnit() {
        return Optional.ofNullable(delegate.getUnit());
    }

    @Override
    public boolean isReusable() {
        return delegate.isReusable();
    }

    @Override
    public HashMap<String, String> getTags() {
        return delegate.getTags();
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return (obj != null)
                && (this.getClass().isAssignableFrom(obj.getClass()))
                && delegate.equals(((InternalMetadataImpl) obj).delegate);
    }

}
