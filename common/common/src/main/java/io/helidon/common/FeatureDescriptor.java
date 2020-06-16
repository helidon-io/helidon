/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.common;

import java.util.Arrays;
import java.util.Objects;

/**
 * Descriptor of a single feature.
 * Contains all information needed to construct the feature tree and native-image warnings.
 */
final class FeatureDescriptor {
    private final HelidonFlavor[] flavors;
    private final String name;
    private final String[] path;
    private final String description;
    private final boolean nativeSupported;
    private final String nativeDescription;

    private FeatureDescriptor(Builder builder) {
        this.flavors = builder.flavors;
        this.name = builder.name;
        this.path = builder.path;
        this.description = builder.description;
        this.nativeSupported = builder.nativeSupported;
        this.nativeDescription = builder.nativeDescription;
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FeatureDescriptor that = (FeatureDescriptor) o;
        return Arrays.equals(flavors, that.flavors)
                && name.equals(that.name)
                && Arrays.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name);
        result = 31 * result + Arrays.hashCode(flavors);
        result = 31 * result + Arrays.hashCode(path);
        return result;
    }

    HelidonFlavor[] flavors() {
        return flavors;
    }

    String name() {
        return name;
    }

    String[] path() {
        return path;
    }

    String description() {
        return description;
    }

    boolean nativeSupported() {
        return nativeSupported;
    }

    String nativeDescription() {
        return (nativeDescription == null ? "" : nativeDescription);
    }

    String stringPath() {
        return String.join("/", path());
    }

    static class Builder implements io.helidon.common.Builder<FeatureDescriptor> {
        private HelidonFlavor[] flavors = new HelidonFlavor[] {HelidonFlavor.SE, HelidonFlavor.MP};
        private String name;
        private String[] path;
        private String description = null;
        private boolean nativeSupported = true;
        private String nativeDescription = null;

        private Builder() {
        }

        @Override
        public FeatureDescriptor build() {
            if (name == null) {
                name = path[path.length - 1];
            }
            return new FeatureDescriptor(this);
        }

        Builder name(String name) {
            if (name == null || name.isEmpty()) {
                return this;
            }
            this.name = name;
            return this;
        }

        Builder path(String path) {
            if (path == null || path.isEmpty()) {
                return this;
            }
            this.path = new String[] {path};
            return this;
        }

        Builder description(String description) {
            if (description == null || description.isEmpty()) {
                return this;
            }
            this.description = description;
            return this;
        }

        Builder nativeSupported(boolean nativeSupported) {
            this.nativeSupported = nativeSupported;
            return this;
        }

        Builder nativeDescription(String description) {
            if (description == null || description.isEmpty()) {
                return this;
            }
            this.nativeDescription = description;
            return this;
        }

        Builder flavor(HelidonFlavor... flavors) {
            this.flavors = flavors;
            return this;
        }

        Builder path(String... path) {
            if (path.length == 0) {
                throw new IllegalArgumentException("Path must have at least one element");
            }
            this.path = path;
            return this;
        }
    }
}
