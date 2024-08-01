/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.metadata;

import java.util.ArrayList;
import java.util.List;

import io.helidon.metadata.hson.Hson;

/**
 * Service descriptor utilities.
 */
public class Descriptors {
    /**
     * Location of the Helidon service registry metadata file.
     */
    public static final String SERVICE_REGISTRY_LOCATION = "META-INF/helidon/service-registry.json";
    private static final int CURRENT_REGISTRY_VERSION = 1;
    private static final int DEFAULT_REGISTRY_VERSION = 1;

    private Descriptors() {
    }

    /**
     * Get all service descriptors from the array of descriptors discovered from classpath (or other source).
     *
     * @param location where was thi array obtained (such as "Classpath URL: ...")
     * @param moduleRegistries array of modules
     * @return a list of descriptor metadata
     * @throws java.lang.IllegalStateException in case the format is not as expected
     */
    public static List<DescriptorMetadata> descriptors(String location, Hson.Array moduleRegistries) {
        List<DescriptorMetadata> descriptors = new ArrayList<>();

        for (Hson.Struct moduleRegistry : moduleRegistries.getStructs()) {
            String moduleName = moduleRegistry.stringValue("module", "unknown");
            int version = moduleRegistry.intValue("version", DEFAULT_REGISTRY_VERSION);
            if (version != CURRENT_REGISTRY_VERSION) {
                throw new IllegalStateException("Invalid registry version: " + version
                                                        + " for module \"" + moduleName + "\""
                                                        + " loaded from \"" + location + "\", "
                                                        + "expected version: \"" + CURRENT_REGISTRY_VERSION + "\"");
            }

            moduleRegistry.structArray("services")
                    .orElseGet(List::of)
                    .stream()
                    .map(it -> DescriptorMetadataImpl.create(moduleName, location, it))
                    .forEach(descriptors::add);
        }
        return descriptors;
    }
}
