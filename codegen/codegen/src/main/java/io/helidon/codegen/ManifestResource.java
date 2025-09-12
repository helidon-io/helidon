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

package io.helidon.codegen;

import java.util.ArrayList;
import java.util.List;

import io.helidon.metadata.MetadataConstants;

/**
 * Support for Helidon manifest file, that lists all manifest resources on the classpath.
 */
public class ManifestResource {
    private final FilerTextResource manifestResource;
    private final List<String> locations;
    private boolean modified;

    private ManifestResource(FilerTextResource manifestResource, List<String> locations) {
        this.manifestResource = manifestResource;
        this.locations = locations;
    }

    /**
     * Create a new instance from the current filer.
     *
     * @param filer filer to find the file, and to write it
     * @return a new instance of the manifest resource
     */
    public static ManifestResource create(CodegenFiler filer) {
        FilerTextResource manifestResource = filer.textResource(MetadataConstants.LOCATION
                                                                        + "/" + MetadataConstants.MANIFEST_FILE);
        List<String> lines = new ArrayList<>(manifestResource.lines());

        if (lines.isEmpty()) {
            lines.add(MetadataConstants.MANIFEST_ID_LINE);
        }
        return new ManifestResource(manifestResource, lines);
    }

    /**
     * Add a new resource location to the manifest.
     *
     * @param resourceLocation resource location to add
     */
    public void add(String resourceLocation) {
        if (!locations.contains(resourceLocation)) {
            locations.add(resourceLocation);
            modified = true;
        }
    }

    /**
     * Write the manifest resource to the file system if modified.
     */
    public void write() {
        if (modified) {
            manifestResource.lines(locations);
            manifestResource.write();
        }
    }
}
