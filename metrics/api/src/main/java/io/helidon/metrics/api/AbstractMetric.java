/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import org.eclipse.microprofile.metrics.Metadata;

/**
 * Common reusable implementation for any category of metric implementation (full-featured, no-op).
 * <p>
 * Helidon relies on this additional behavior beyond that provided by MP {@code Metric}.
 * </p>
 */
public abstract class AbstractMetric implements BaseMetric {

    private final String registryType;
    private final Metadata metadata;
    private boolean isDeleted;

    protected AbstractMetric(String registryType, Metadata metadata) {
        this.registryType = registryType;
        this.metadata = metadata;
    }

    @Override
    public Metadata metadata() {
        return metadata;
    }

    @Override
    public void markAsDeleted() {
        isDeleted = true;
    }

    @Override
    public boolean isDeleted() {
        return isDeleted;
    }

    @Override
    public String registryType() {
        return registryType;
    }
}
