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

/**
 * Constants used during metadata discovery.
 * <p>
 * For backward compatibility, we must explicitly list the files that should be loaded, as we expected their location
 * directly under {@link #LOCATION}, without a directory structure and a manifest file.
 */
public final class MetadataConstants {
    /**
     * Default location of the metadata files, this is expected to be a directory that is the same in every
     * jar file. Files in this location will be explicitly added only if they match the provided metadata file name.
     */
    public static final String LOCATION = "META-INF/helidon";
    /**
     * Custom media type mappings.
     * <p>
     * File name is: {@value}
     */
    public static final String MEDIA_TYPES_FILE = "media-types.properties";
    /**
     * Service registry JSON.
     * <p>
     * File name is: {@value}
     */
    public static final String SERVICE_REGISTRY_FILE = "service-registry.json";
    /**
     * List of services to load from Java {@link java.util.ServiceLoader} for Service registry.
     * <p>
     * File name is: {@value}
     */
    public static final String SERVICE_LOADER_FILE = "service.loader";
    /**
     * Feature registry JSON.
     * <p>
     * File name is: {@value}
     */
    public static final String FEATURE_REGISTRY_FILE = "feature-registry.json";
    /**
     * Feature metadata properties.
     * This file is deprecated, because it cannot be merged.
     * <p>
     * File name is: {@value}
     *
     * @deprecated Use {@link #FEATURE_REGISTRY_FILE} instead.
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
    public static final String FEATURE_METADATA_FILE = "feature-metadata.properties";
    /**
     * Configuration metadata JSON.
     * <p>
     * File name is: {@value}
     */
    public static final String CONFIG_METADATA_FILE = "config-metadata.json";
    /**
     * Configuration for Java deserialization.
     * <p>
     * File name is: {@value}
     */
    public static final String SERIAL_CONFIG_FILE = "serial-config.properties";
    /**
     * Name of the manifest file, expected under the configured root location.
     */
    public static final String MANIFEST_FILE = "manifest";
    /**
     * Line that must be exactly once in each module's manifest file.
     * It is used to identify that we have (correctly) merged manifest files when running a shaded jar.
     * <p>
     * Value: {@value}
     */
    public static final String MANIFEST_ID_LINE = "#HELIDON MANIFEST#";

    private MetadataConstants() {
    }
}
