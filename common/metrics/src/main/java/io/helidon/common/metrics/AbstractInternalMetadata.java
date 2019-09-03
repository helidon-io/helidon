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
package io.helidon.common.metrics;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.microprofile.metrics.MetricType;

/**
 * Version-neutral implementation of the common elements of the bridge metadata
 * interface.
 * <p>
 * This implementation delegates to a version-specific delegate.
 */
public abstract class AbstractInternalMetadata implements InternalBridge.Metadata {


    private final org.eclipse.microprofile.metrics.Metadata delegate;

    /**
     * Instantiates a new version-neutral metadata instance using the specified
     * version-specific delegate.
     *
     * @param delegate the version-specific delegate
     */
    public AbstractInternalMetadata(org.eclipse.microprofile.metrics.Metadata delegate) {
        this.delegate = delegate;
    }

    /**
     *
     * @return the version-specific delegate
     */
    protected final org.eclipse.microprofile.metrics.Metadata delegate() {
        return delegate;
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
    public abstract Optional<String> getDescription();

    @Override
    public String getType() {
        return delegate.getType();
    }

    @Override
    public MetricType getTypeRaw() {
        return delegate.getTypeRaw();
    }

    @Override
    public abstract Optional<String> getUnit();

    @Override
    public boolean isReusable() {
        return delegate.isReusable();
    }

    @Override
    public abstract Map<String, String> getTags();

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this.getClass().isAssignableFrom(Objects.requireNonNull(obj).getClass())) {
            throw new IllegalArgumentException("Expected argument of type "
                + this.getClass().getName() + " but received "
                + obj.getClass().getName());
        }
        return delegate.equals(obj);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

}
