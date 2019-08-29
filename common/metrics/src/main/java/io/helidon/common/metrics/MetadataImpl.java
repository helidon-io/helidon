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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.microprofile.metrics.MetricType;

/**
 *
 */
class MetadataImpl implements InternalBridge.Metadata {

    private final String name;
    private final String displayName;
    private final String description;
    private final MetricType type;
    private final String unit;
    private final boolean reusable;
    private Map<String, String> tags;

    MetadataImpl(String name, String displayName, String description,
            MetricType type, String unit, boolean reusable, Map<String, String> tags) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.unit = unit;
        this.reusable = reusable;
        this.tags = tags != null ? new HashMap<>(tags) : Collections.emptyMap();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return displayName != null ? displayName : name;
    }

    @Override
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    @Override
    public String getType() {
        return getTypeRaw().toString();
    }

    @Override
    public MetricType getTypeRaw() {
        return type != null ? type : MetricType.INVALID;
    }

    @Override
    public Optional<String> getUnit() {
        return Optional.ofNullable(unit);
    }

    @Override
    public boolean isReusable() {
        return reusable;
    }

    @Override
    public Map<String, String> getTags() {
        return tags.isEmpty() ? Collections.emptyMap() : new HashMap<>(tags);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.name);
        hash = 41 * hash + Objects.hashCode(this.displayName);
        hash = 41 * hash + Objects.hashCode(this.description);
        hash = 41 * hash + Objects.hashCode(this.type);
        hash = 41 * hash + Objects.hashCode(this.unit);
        hash = 41 * hash + (this.reusable ? 1 : 0);
        hash = 41 * hash + Objects.hashCode(this.tags);
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
        final MetadataImpl other = (MetadataImpl) obj;
        if (this.reusable != other.reusable) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.displayName, other.displayName)) {
            return false;
        }
        if (!Objects.equals(this.description, other.description)) {
            return false;
        }
        if (!Objects.equals(this.unit, other.unit)) {
            return false;
        }
        if (this.type != other.type) {
            return false;
        }
        if (!Objects.equals(this.tags, other.tags)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultMetadata{");
        sb.append("name='").append(name).append('\'');
        sb.append(", type=").append(type);
        sb.append(", unit='").append(unit).append('\'');
        sb.append(", reusable=").append(reusable);
        if(description != null) {
            sb.append(", description='").append(description).append('\'');
        }
        else {
            sb.append(", description=null");
        }
        if(displayName != null) {
            sb.append(", displayName='").append(displayName).append('\'');
        }
        else {
            sb.append(", displayName=null");
        }
        sb.append('}');
        return sb.toString();
    }
}
