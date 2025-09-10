/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.metadata;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Configuration of metadata handling.
 *
 * @see #builder()
 * @see #create()
 */
public interface MetadataDiscoveryConfig {
    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static MetadataDiscoveryConfig.Builder builder() {
        return new MetadataDiscoveryConfig.Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static MetadataDiscoveryConfig.Builder builder(MetadataDiscoveryConfig instance) {
        return MetadataDiscoveryConfig.builder().from(instance);
    }

    /**
     * Create a new instance with default values.
     *
     * @return a new instance
     */
    static MetadataDiscoveryConfig create() {
        return MetadataDiscoveryConfig.builder().buildPrototype();
    }

    /**
     * Mode of metadata handling.
     *
     * @return mode to use, defaults to {@link MetadataDiscovery.Mode#AUTO}
     */
    MetadataDiscovery.Mode mode();

    /**
     * Location on classpath where we look for the manifest file, and where we start when doing classpath scanning.
     *
     * @return location on classpath, defaults to {@link MetadataDiscovery#LOCATION}
     */
    String location();

    /**
     * File names of metadata files to find.
     *
     * @return set of metadata file names, defaults to {@link MetadataDiscovery#METADATA_FILES}
     */
    Set<String> metadataFiles();

    /**
     * File name of the manifest file.
     *
     * @return name of the manifest file expected in {@link #location()}
     *         defaults to {@link MetadataDiscovery#MANIFEST_FILE}
     */
    String manifestFile();

    /**
     * Classloader to use.
     *
     * @return class loader
     */
    ClassLoader classLoader();

    /**
     * Fluent API builder for {@link MetadataDiscoveryConfig}.
     */
    class Builder {

        private final Set<String> metadataFiles = new LinkedHashSet<>(MetadataDiscovery.METADATA_FILES);
        private boolean isMetadataFilesMutated;
        private ClassLoader classLoader;
        private MetadataDiscovery.Mode mode = MetadataDiscovery.Mode.AUTO;
        private String location = MetadataDiscovery.LOCATION;
        private String manifestFile = MetadataDiscovery.MANIFEST_FILE;

        /**
         * Protected to support extensibility.
         */
        private Builder() {
        }

        /**
         * Update this builder from an existing prototype instance. This method disables automatic service discovery.
         *
         * @param prototype existing prototype to update this builder from
         * @return updated builder instance
         */
        public Builder from(MetadataDiscoveryConfig prototype) {
            mode(prototype.mode());
            location(prototype.location());
            if (!isMetadataFilesMutated) {
                metadataFiles.clear();
            }
            addMetadataFiles(prototype.metadataFiles());
            manifestFile(prototype.manifestFile());
            classLoader(prototype.classLoader());
            return this;
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public Builder from(Builder builder) {
            mode(builder.mode());
            builder.location().ifPresent(this::location);
            if (isMetadataFilesMutated) {
                if (builder.isMetadataFilesMutated) {
                    addMetadataFiles(builder.metadataFiles);
                }
            } else {
                metadataFiles.clear();
                addMetadataFiles(builder.metadataFiles);
            }
            builder.manifestFile().ifPresent(this::manifestFile);
            builder.classLoader().ifPresent(this::classLoader);
            return this;
        }

        /**
         * Mode of metadata handling.
         *
         * @param mode mode to use, defaults to {@link MetadataDiscovery.Mode#AUTO}
         * @return updated builder instance
         * @see #mode()
         */
        public Builder mode(MetadataDiscovery.Mode mode) {
            Objects.requireNonNull(mode);
            this.mode = mode;
            return this;
        }

        /**
         * Location on classpath where we look for the manifest file, and where we start when doing classpath scanning.
         *
         * @param location location on classpath, defaults to {@link MetadataDiscovery#LOCATION}
         * @return updated builder instance
         * @see #location()
         */
        public Builder location(String location) {
            Objects.requireNonNull(location);
            this.location = location;
            return this;
        }

        /**
         * File names of metadata files to find.
         *
         * @param metadataFiles list of metadata file names, defaults to {@link MetadataDiscovery#METADATA_FILES}
         * @return updated builder instance
         * @see #metadataFiles()
         */
        public Builder metadataFiles(List<String> metadataFiles) {
            Objects.requireNonNull(metadataFiles);
            isMetadataFilesMutated = true;
            this.metadataFiles.clear();
            this.metadataFiles.addAll(metadataFiles);
            return this;
        }

        /**
         * File names of metadata files to find.
         *
         * @param metadataFiles list of metadata file names, defaults to {@link MetadataDiscovery#METADATA_FILES}
         * @return updated builder instance
         * @see #metadataFiles()
         */
        public Builder addMetadataFiles(Set<String> metadataFiles) {
            Objects.requireNonNull(metadataFiles);
            isMetadataFilesMutated = true;
            this.metadataFiles.addAll(metadataFiles);
            return this;
        }

        /**
         * File name of the manifest file.
         *
         * @param manifestFile name of the manifest file expected in {@link #location()}
         *                     defaults to {@link MetadataDiscovery#MANIFEST_FILE}
         * @return updated builder instance
         * @see #manifestFile()
         */
        public Builder manifestFile(String manifestFile) {
            Objects.requireNonNull(manifestFile);
            this.manifestFile = manifestFile;
            return this;
        }

        /**
         * Classloader to use.
         *
         * @param classLoader class loader
         * @return updated builder instance
         * @see #classLoader()
         */
        public Builder classLoader(ClassLoader classLoader) {
            Objects.requireNonNull(classLoader);
            this.classLoader = classLoader;
            return this;
        }

        /**
         * Mode of metadata handling.
         *
         * @return the mode
         */
        public MetadataDiscovery.Mode mode() {
            return mode;
        }

        /**
         * Location on classpath where we look for the manifest file, and where we start when doing classpath scanning.
         *
         * @return the location
         */
        public Optional<String> location() {
            return Optional.ofNullable(location);
        }

        /**
         * File names of metadata files to find.
         *
         * @return the metadata files
         */
        public Set<String> metadataFiles() {
            return metadataFiles;
        }

        /**
         * File name of the manifest file.
         *
         * @return the manifest file
         */
        public Optional<String> manifestFile() {
            return Optional.ofNullable(manifestFile);
        }

        /**
         * Classloader to use.
         *
         * @return the class loader
         */
        public Optional<ClassLoader> classLoader() {
            return Optional.ofNullable(classLoader);
        }

        @Override
        public String toString() {
            return "MetadataConfigBuilder{"
                    + "mode=" + mode + ","
                    + "location=" + location + ","
                    + "metadataFiles=" + metadataFiles + ","
                    + "manifestFile=" + manifestFile + ","
                    + "classLoader=" + classLoader
                    + "}";
        }

        /**
         * Build an instance of metadata configuration.
         *
         * @return metadata configuration from this builder
         */
        public MetadataDiscoveryConfig buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new MetadataConfigImpl(this);
        }

        /**
         * Create an instance of {@link MetadataDiscovery} based on this builder.
         *
         * @return configured metadata instance
         */
        public MetadataDiscovery build() {
            return MetadataDiscovery.create(buildPrototype());
        }

        /**
         * Handles providers and decorators.
         */
        protected void preBuildPrototype() {
            if (classLoader == null) {
                classLoader = Thread.currentThread().getContextClassLoader();
                if (classLoader == null) {
                    classLoader = MetadataDiscoveryConfig.class.getClassLoader();
                }
            }
        }

        /**
         * Validates required properties.
         */
        protected void validatePrototype() {
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class MetadataConfigImpl implements MetadataDiscoveryConfig {

            private final ClassLoader classLoader;
            private final Set<String> metadataFiles;
            private final MetadataDiscovery.Mode mode;
            private final String location;
            private final String manifestFile;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected MetadataConfigImpl(Builder builder) {
                this.mode = builder.mode();
                this.location = builder.location().get();
                this.metadataFiles = Set.copyOf(builder.metadataFiles());
                this.manifestFile = builder.manifestFile().get();
                this.classLoader = builder.classLoader().get();
            }

            @Override
            public MetadataDiscovery.Mode mode() {
                return mode;
            }

            @Override
            public String location() {
                return location;
            }

            @Override
            public Set<String> metadataFiles() {
                return metadataFiles;
            }

            @Override
            public String manifestFile() {
                return manifestFile;
            }

            @Override
            public ClassLoader classLoader() {
                return classLoader;
            }

            @Override
            public String toString() {
                return "MetadataConfig{"
                        + "mode=" + mode + ","
                        + "location=" + location + ","
                        + "metadataFiles=" + metadataFiles + ","
                        + "manifestFile=" + manifestFile + ","
                        + "classLoader=" + classLoader
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof MetadataDiscoveryConfig other)) {
                    return false;
                }
                return Objects.equals(mode, other.mode())
                        && Objects.equals(location, other.location())
                        && Objects.equals(metadataFiles, other.metadataFiles())
                        && Objects.equals(manifestFile, other.manifestFile())
                        && Objects.equals(classLoader, other.classLoader());
            }

            @Override
            public int hashCode() {
                return Objects.hash(mode, location, metadataFiles, manifestFile, classLoader);
            }

        }

    }
}
