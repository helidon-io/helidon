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

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.metrics.InternalBridge.Metadata;

import org.eclipse.microprofile.metrics.MetricType;

/**
 * Metrics 2.0-based implementation of the version-neutral {@code Metadata} interface.
 */
class InternalMetadataImpl implements Metadata {

    private final org.eclipse.microprofile.metrics.Metadata delegate;

    /**
     * Creates a new metadata instance, delegating to the specified 2.0 metadata.
     * @param delegate
     */
    InternalMetadataImpl(org.eclipse.microprofile.metrics.Metadata delegate) {
        this.delegate = delegate;
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
        return delegate.getDescription();
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
        return delegate.getUnit();
    }

    @Override
    public boolean isReusable() {
        return delegate.isReusable();
    }

    @Override
    public Map<String, String> getTags() {
        return Collections.emptyMap();
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return (obj != null)
                && (this.getClass().isAssignableFrom(obj.getClass()))
                && delegate.equals(((InternalMetadataImpl) obj).delegate);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

}
