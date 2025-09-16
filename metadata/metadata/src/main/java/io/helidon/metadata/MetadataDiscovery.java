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
     * Create a new metadata discovery instance with an explicit mode.
     *
     * @param mode mode of discovery, see {@link Mode} for details
     * @return metadata instance
     */
    static MetadataDiscovery create(Mode mode) {
        return MetadataDiscoveryImpl.create(mode);
    }

    /**
     * Get or create the metadata discovery instance for the current context classloader.
     * <p>
     * Instances are created using {@link #create(io.helidon.metadata.MetadataDiscovery.Mode)},
     * with mode set to {@link Mode#AUTO}.
     * <p>
     * If the current context classloader is {@code null}, the classloader for this class is used instead
     *
     * @return an instance of metadata
     * @see #create(io.helidon.metadata.MetadataDiscovery.Mode)
     */
    static MetadataDiscovery instance() {
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
         * <li>If {@link MetadataConstants#MANIFEST_FILE} is found, and it is considered merged (more than one line with
         * {@link MetadataConstants#MANIFEST_ID_LINE} exists),
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
