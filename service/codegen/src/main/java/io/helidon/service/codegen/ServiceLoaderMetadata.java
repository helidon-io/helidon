/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.service.codegen;

import java.util.ArrayList;
import java.util.List;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenFiler;
import io.helidon.codegen.FilerTextResource;
import io.helidon.common.types.ModuleTypeInfo;
import io.helidon.metadata.MetadataConstants;

final class ServiceLoaderMetadata {
    private static final String HEADER =
            "# List of service contracts we want to support either from service registry, or from service loader";

    private ServiceLoaderMetadata() {
    }

    static void write(CodegenFiler filer, ModuleTypeInfo module) {
        List<String> contracts = module.uses()
                .stream()
                .map(it -> it.service().fqName())
                .sorted()
                .toList();
        if (contracts.isEmpty()) {
            throw new CodegenException("Module annotated for service loader discovery must declare at least one uses directive",
                                       module.originatingElementValue());
        }

        String resourceLocation = MetadataConstants.LOCATION
                + "/" + module.name()
                + "/" + MetadataConstants.SERVICE_LOADER_FILE;
        FilerTextResource resource = filer.manifest().deferredTextResource(resourceLocation,
                                                                          module.originatingElementValue());
        List<String> existingLines = resource.lines();
        List<String> newLines = new ArrayList<>(contracts.size() + 1);

        newLines.add(HEADER);
        newLines.addAll(contracts);

        if (!newLines.equals(existingLines)) {
            resource.lines(newLines);
            resource.write();
        }
        filer.manifest().add(resourceLocation);
    }
}
