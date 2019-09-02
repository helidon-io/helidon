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

import io.helidon.common.metrics.AbstractInternalMetadata;
import java.util.HashMap;
import java.util.Optional;

import org.eclipse.microprofile.metrics.MetricType;

/**
 *
 */
class InternalMetadataImpl extends AbstractInternalMetadata {

    InternalMetadataImpl(String name, String displayName, String description,
            MetricType type, String unit) {
        super(new org.eclipse.microprofile.metrics.Metadata(
                name, displayName, description, type, unit));
    }

    InternalMetadataImpl(String name, String displayName, String description,
            MetricType type, String unit, String tags) {
        super(new org.eclipse.microprofile.metrics.Metadata(
                name, displayName, description, type, unit, tags));
    }

    InternalMetadataImpl(String name, String displayName, String description,
            MetricType type, String unit, boolean isReusable, String tags) {
        super(new org.eclipse.microprofile.metrics.Metadata(
                name, displayName, description, type, unit, tags));
        delegate().setReusable(isReusable);
    }

    @Override
    public Optional<String> getDescription() {
        return Optional.ofNullable(delegate().getDescription());
    }

    @Override
    public Optional<String> getUnit() {
        return Optional.ofNullable(delegate().getUnit());
    }

    @Override
    public HashMap<String, String> getTags() {
        return delegate().getTags();
    }

}
