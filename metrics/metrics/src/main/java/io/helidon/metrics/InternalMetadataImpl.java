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

import io.helidon.common.metrics.InternalBridge.Metadata;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.microprofile.metrics.MetricType;

/**
 *
 */
class InternalMetadataImpl implements Metadata {

    private final org.eclipse.microprofile.metrics.Metadata delegate;

    InternalMetadataImpl(String name, String displayName, String description,
            MetricType type, String unit) {
        delegate = new org.eclipse.microprofile.metrics.Metadata(
                name, displayName, description, type, unit);
    }

    InternalMetadataImpl(String name, String displayName, String description,
            MetricType type, String unit, String tags) {
        delegate = new org.eclipse.microprofile.metrics.Metadata(
                name, displayName, description, type, unit, tags);
    }

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
        if (!InternalMetadataImpl.class.isAssignableFrom(Objects.requireNonNull(obj).getClass())) {
            throw new IllegalArgumentException("Expected argument of type "
                + InternalMetadataImpl.class.getName() + " but received "
                + obj.getClass().getName());
        }
        return delegate.equals(obj);
    }

}
