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

import java.util.List;

/**
 * Helidon metadata discovery.
 */
public interface MetadataDiscovery {
    /**
     * Default location of the metadata files, this is expected to be a directory that is the same in every
     * jar file. Files in this location will be explicitly added only if they match the provided metadata file name.
     */
    String LOCATION = "META-INF/helidon";
    /**
     * Custom media type mappings.
     * <p>
     * File name is: {@value}
     */
    String MEDIA_TYPES_FILE = "media-types.properties";
    /**
     * Service registry JSON.
     * <p>
     * File name is: {@value}
     */
    String SERVICE_REGISTRY_FILE = "service-registry.json";
    /**
     * List of services to load from Java {@link java.util.ServiceLoader} for Service registry.
     * <p>
     * File name is: {@value}
     */
    String SERVICE_LOADER_FILE = "service.loader";
    /**
     * Feature registry JSON.
     * <p>
     * File name is: {@value}
     */
    String FEATURE_REGISTRY_FILE = "feature-registry.json";
    /**
     * Feature metadata properties.
     * This file is deprecated, because it cannot be merged.
     * <p>
     * File name is: {@value}
     *
     * @deprecated Use {@link #FEATURE_REGISTRY_FILE} instead.
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
    String FEATURE_METADATA_FILE = "feature-metadata.properties";
    /**
     * Configuration metadata JSON.
     * <p>
     * File name is: {@value}
     */
    String CONFIG_METADATA_FILE = "config-metadata.json";
    /**
     * Configuration for Java deserialization.
     * <p>
     * File name is: {@value}
     */
    String SERIAL_CONFIG_FILE = "serial-config.properties";

    /**
     * Name of the manifest file, expected under the configured root location.
     */
    String MANIFEST_FILE = "manifest";
    /**
     * Line that must be exactly once in each module's manifest file.
     * It is used to identify that we have (correctly) merged manifest files when running a shaded jar.
     * <p>
     * Value: {@value}
     */
    String MANIFEST_ID_LINE = "#HELIDON MANIFEST#";

    /**
     * Create a new metadata instance with default configuration - current context class-loader,
     * default manifest location, and default metadata file names.
     *
     * @param mode mode of discovery, see {@link Mode} for details
     * @return metadata instance
     * @see #LOCATION
     * @see #MANIFEST_FILE
     */
    static MetadataDiscovery create(Mode mode) {
        return MetadataDiscoveryImpl.create(mode);
    }

    /**
     * As metadata is based on classpath, it can be used statically.
     * This method will provide an instance created using {@link #create(io.helidon.metadata.MetadataDiscovery.Mode)}
     * with mode set to {@link Mode#AUTO}.
     * For custom based instance, make sure to re-use the instance yourself, to avoid multiple discoveries.
     * <p>
     * This method does a check that verifies the static instance is for the current context class loader, to avoid
     * problems in environments where the class loader may change (i.e. Maven build).
     *
     * @return an instance of metadata
     * @see #create(io.helidon.metadata.MetadataDiscovery.Mode)
     */
    static MetadataDiscovery getInstance() {
        return MetadataDiscoveryImpl.InstanceHolder.getInstance();
    }

    /**
     * List all metadata instances by file name.
     *
     * @param fileName name of the file
     * @return list of metadata instances, empty list if none found (never null)
     * @throws java.lang.NullPointerException if {@code fileName} is null
     */
    List<MetadataFile> list(String fileName);

    /**
     * Modes of discovery.
     * Can be controlled through system property {@value MetadataDiscoveryImpl#SYSTEM_PROPERTY_MODE}.
     */
    enum Mode {
        /**
         * Automatic mode, works as follows:
         * <ul>
         * <li>If {@link #MANIFEST_FILE} is found, and it is considered merged (more than one line with
         * {@link #MANIFEST_ID_LINE} exists),
         *          it is used to discover metadata files.</li>
         * <li>If there is one or less {@code META-INF/MANIFEST.MF} files on classpath, classpath discovery is used</li>
         * <li>Classpath discovery using all manifests is used</li>
         * </ul>
         */
        AUTO,
        /**
         * Uses resources discovered from classpath, expecting duplicate resources.
         * <p>
         * NOTE: This approach will not work with unmerged manifests.
         */
        RESOURCES,
        /**
         * Discovers metadata files by scanning the file entries defined by the {@code java.class.path} and
         * {@code jdk.module.path} system properties.
         * <p>
         * NOTE: this adds startup performance overhead, and will not work with unusual classloaders
         * (such as GraalVM native-image).
         */
        SCANNING,
        /**
         * Do not do any discovery.
         */
        NONE
    }
}
