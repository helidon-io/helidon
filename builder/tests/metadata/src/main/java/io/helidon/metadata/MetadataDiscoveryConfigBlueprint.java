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

import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration of metadata handling.
 */
@Prototype.Blueprint
interface MetadataDiscoveryConfigBlueprint {
    /**
     * Mode of metadata handling.
     *
     * @return mode to use, defaults to {@link MetadataDiscovery.Mode#AUTO}
     */
    @Option.Default("AUTO")
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
}
