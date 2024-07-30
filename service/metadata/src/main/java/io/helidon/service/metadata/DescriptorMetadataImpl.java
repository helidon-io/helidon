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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;
import io.helidon.metadata.hson.Hson;

record DescriptorMetadataImpl(String registryType,
                              double weight,
                              TypeName descriptorType,
                              Set<TypeName> contracts) implements DescriptorMetadata {

    private static final int CURRENT_DESCRIPTOR_VERSION = 1;
    private static final int DEFAULT_DESCRIPTOR_VERSION = 1;

    static DescriptorMetadata create(String moduleName, String location, Hson.Object service) {
        int version = service.intValue("version", DEFAULT_DESCRIPTOR_VERSION);
        if (version != CURRENT_DESCRIPTOR_VERSION) {
            throw new IllegalStateException("Invalid descriptor version: " + version
                                                    + " for module \"" + moduleName + "\""
                                                    + " loaded from \"" + location + "\", "
                                                    + "expected version: \"" + CURRENT_DESCRIPTOR_VERSION + "\","
                                                    + " descriptor (if available): "
                                                    + service.stringValue("descriptor", "N/A"));
        }

        String type = service.stringValue("type", REGISTRY_TYPE_CORE);
        TypeName descriptor = service.stringValue("descriptor")
                .map(TypeName::create)
                .orElseThrow(() -> new IllegalStateException("Could not parse service metadata "
                                                                     + " for module \"" + moduleName + "\""
                                                                     + " loaded from \"" + location + "\", "
                                                                     + "missing \"descriptor\" value"));
        double weight = service.doubleValue("weight", Weighted.DEFAULT_WEIGHT);
        Set<TypeName> contracts = service.stringArray("contracts")
                .orElseGet(List::of)
                .stream()
                .map(TypeName::create)
                .collect(Collectors.toSet());

        return new DescriptorMetadataImpl(type, weight, descriptor, contracts);
    }

    @Override
    public Hson.Object toHson() {
        var builder = Hson.objectBuilder();

        if (!registryType.equals(REGISTRY_TYPE_CORE)) {
            builder.set("type", registryType);
        }
        if (weight != Weighted.DEFAULT_WEIGHT) {
            builder.set("weight", weight);
        }
        builder.set("descriptor", descriptorType.fqName());
        builder.setStrings("contracts", contracts.stream()
                .map(TypeName::fqName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toUnmodifiableList()));

        return builder.build();
    }
}
