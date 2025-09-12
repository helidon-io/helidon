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

record MetadataDiscoveryContext(ClassLoader classLoader, Set<String> metadataFiles, String location, String manifestFile) {
    @SuppressWarnings("removal")
    private static final Set<String> METADATA_FILES = Set.of(MetadataConstants.SERVICE_REGISTRY_FILE,
                                                             MetadataConstants.FEATURE_REGISTRY_FILE,
                                                             MetadataConstants.CONFIG_METADATA_FILE,
                                                             MetadataConstants.SERVICE_LOADER_FILE,
                                                             MetadataConstants.SERIAL_CONFIG_FILE,
                                                             MetadataConstants.MEDIA_TYPES_FILE,
                                                             MetadataConstants.FEATURE_METADATA_FILE);

    static MetadataDiscoveryContext create(ClassLoader cl) {
        return new MetadataDiscoveryContext(cl,
                                            METADATA_FILES,
                                            MetadataConstants.LOCATION,
                                            MetadataConstants.MANIFEST_FILE);
    }
}
