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

package io.helidon.codegen.apt;

import java.util.Arrays;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.FilerResource;

class FilerResourceImpl implements FilerResource {
    private final Filer filer;
    private final String location;
    private final Element[] originatingElements;
    private final FileObject originalResource; // may be null

    private byte[] currentBytes;

    private boolean modified;

    FilerResourceImpl(Filer filer, String location, Element[] originatingElements) {
        this.filer = filer;
        this.location = location;
        this.originatingElements = originatingElements;
        this.originalResource = null;
        this.currentBytes = new byte[0];
    }

    FilerResourceImpl(Filer filer,
                      String location,
                      Element[] originatingElements,
                      FileObject originalResource,
                      byte[] existingBytes) {
        this.filer = filer;
        this.location = location;
        this.originatingElements = originatingElements;
        this.originalResource = originalResource;
        this.currentBytes = existingBytes;
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(currentBytes, currentBytes.length);
    }

    @Override
    public void bytes(byte[] newBytes) {
        currentBytes = Arrays.copyOf(newBytes, newBytes.length);
        modified = true;
    }

    @Override
    public void write() {
        if (modified) {
            if (originalResource != null) {
                originalResource.delete();
            }
            try {
                FileObject newResource = filer.createResource(StandardLocation.CLASS_OUTPUT,
                                                              "",
                                                              location,
                                                              originatingElements);
                try (var os = newResource.openOutputStream()) {
                    os.write(currentBytes);
                }
            } catch (Exception e) {
                throw new CodegenException("Failed to create resource: " + location, e);
            }
        }
    }
}
