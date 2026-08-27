/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.metadata.MetadataConstants;

/**
 * Support for Helidon manifest file, that lists all manifest resources on the classpath.
 */
public class ManifestResource {
    private final CodegenFiler filer;
    private final FilerTextResource manifestResource;
    private final List<String> locations;
    private final Map<String, DeferredTextResource> deferredTextResources = new LinkedHashMap<>();
    private boolean modified;

    private ManifestResource(CodegenFiler filer, FilerTextResource manifestResource, List<String> locations) {
        this.filer = filer;
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
        return new ManifestResource(filer, manifestResource, lines);
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
     * Obtain a shared text resource that is written when this manifest is written.
     * All callers requesting the same location contribute to the same in-memory resource, so codegen extensions can
     * update it independently without attempting to create the file more than once during annotation processing.
     * Calling {@link FilerTextResource#write()} stages the current content; the physical write is deferred until
     * {@link #write()} is called.
     *
     * @param resourceLocation resource location in the classes output directory
     * @param originatingElements elements that caused this resource to be generated
     * @return shared deferred resource
     */
    public FilerTextResource deferredTextResource(String resourceLocation, Object... originatingElements) {
        Objects.requireNonNull(resourceLocation);
        Objects.requireNonNull(originatingElements);
        return deferredTextResources.computeIfAbsent(resourceLocation,
                                                     _ -> new DeferredTextResource(
                                                             filer.textResource(resourceLocation, originatingElements)));
    }

    /**
     * Write staged text resources, and write the manifest resource to the file system if modified.
     */
    public void write() {
        deferredTextResources.values().forEach(DeferredTextResource::writeNow);
        if (modified) {
            manifestResource.lines(locations);
            manifestResource.write();
        }
    }

    private static final class DeferredTextResource implements FilerTextResource {
        private final FilerTextResource delegate;
        private boolean writeRequested;

        private DeferredTextResource(FilerTextResource delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<String> lines() {
            return delegate.lines();
        }

        @Override
        public void lines(List<String> newLines) {
            delegate.lines(newLines);
        }

        @Override
        public void write() {
            writeRequested = true;
        }

        private void writeNow() {
            if (writeRequested) {
                delegate.write();
                writeRequested = false;
            }
        }
    }
}
