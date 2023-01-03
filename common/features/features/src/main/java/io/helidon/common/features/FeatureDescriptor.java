/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

package io.helidon.common.features;

import java.util.Arrays;
import java.util.Objects;

import io.helidon.common.features.api.HelidonFlavor;

/**
 * Descriptor of a single feature.
 * Contains all information needed to construct the feature tree and native-image warnings.
 */
final class FeatureDescriptor implements Comparable<FeatureDescriptor> {
    private final HelidonFlavor[] flavors;
    private final HelidonFlavor[] notFlavors;
    private final String name;
    private final String since;
    private final String[] path;
    private final String description;
    private final boolean nativeSupported;
    private final String nativeDescription;
    private final boolean preview;
    private final boolean incubating;
    private final String module;
    private final boolean deprecated;
    private final String deprecatedSince;


    private FeatureDescriptor(Builder builder) {
        this.flavors = builder.flavors;
        this.notFlavors = builder.notFlavors;
        this.name = builder.name;
        this.since = builder.since;
        this.module = builder.module;
        this.path = builder.path;
        this.description = builder.description;
        this.nativeSupported = builder.nativeSupported;
        this.nativeDescription = builder.nativeDescription;
        this.preview = builder.preview;
        this.incubating = builder.incubating;
        this.deprecated = builder.deprecated;
        this.deprecatedSince = builder.deprecatedSince;
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name);
        result = 31 * result + Arrays.hashCode(flavors);
        result = 31 * result + Arrays.hashCode(path);
        return result;
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
    public String toString() {
        return name;
    }

    @Override
    public int compareTo(FeatureDescriptor o) {
        for (int i = 0; i < path.length && i < o.path.length; i++) {
            int result = path[i].compareTo(o.path[i]);
            if (result != 0) {
                return result;
            }
        }
        // same base path
        return path.length - o.path.length;
    }

    String module() {
        return module;
    }

    HelidonFlavor[] flavors() {
        return flavors;
    }

    HelidonFlavor[] notFlavors() {
        return notFlavors;
    }

    boolean not(HelidonFlavor flavor) {
        for (HelidonFlavor notFlavor : notFlavors) {
            if (flavor == notFlavor) {
                return true;
            }
        }
        return false;
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

    boolean preview() {
        return preview;
    }

    String since() {
        return since;
    }

    boolean incubating() {
        return incubating;
    }

    boolean deprecated() {
        return deprecated;
    }

    String deprecatedSince() {
        return deprecatedSince;
    }

    boolean hasFlavor(HelidonFlavor expected) {
        for (HelidonFlavor flavor : flavors) {
            if (flavor == expected) {
                return true;
            }
        }
        return false;
    }

    static class Builder implements io.helidon.common.Builder<Builder, FeatureDescriptor> {
        private String module;
        private HelidonFlavor[] flavors;
        private HelidonFlavor[] notFlavors;
        private String name;
        private String since;
        private String[] path;
        private String description = null;
        private boolean nativeSupported = true;
        private String nativeDescription = null;
        private boolean incubating;
        private boolean preview;
        private boolean deprecated;
        private String deprecatedSince;


        private Builder() {
        }

        @Override
        public FeatureDescriptor build() {
            return new FeatureDescriptor(this);
        }

        Builder module(String module) {
            this.module = module;
            return this;
        }

        Builder since(String version) {
            this.since = version;
            return this;
        }

        Builder preview(boolean preview) {
            this.preview = preview;
            return this;
        }

        Builder deprecated(boolean deprecated) {
            this.deprecated = deprecated;
            return this;
        }

        Builder deprecatedSince(String version) {
            this.deprecatedSince = version;
            return this;
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

        Builder notFlavor(HelidonFlavor... flavors) {
            this.notFlavors = flavors;
            return this;
        }

        Builder path(String... path) {
            if (path.length == 0) {
                throw new IllegalArgumentException("Path must have at least one element");
            }
            this.path = path;
            return this;
        }

        Builder incubating(boolean incubating) {
            this.incubating = incubating;
            return this;
        }
    }
}
