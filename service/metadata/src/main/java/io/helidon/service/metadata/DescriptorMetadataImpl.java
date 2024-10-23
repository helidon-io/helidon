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
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.metadata.hson.Hson;

record DescriptorMetadataImpl(String registryType,
                              double weight,
                              TypeName descriptorType,
                              Set<ResolvedType> contracts,
                              Set<ResolvedType> factoryContracts) implements DescriptorMetadata {

    private static final int CURRENT_DESCRIPTOR_VERSION = 1;
    private static final int DEFAULT_DESCRIPTOR_VERSION = 1;
    private static final String HSON_TYPE = "type";
    private static final String HSON_WEIGHT = "weight";
    private static final String HSON_DESCRIPTOR = "descriptor";
    private static final String HSON_CONTRACTS = "contracts";
    private static final String HSON_FACTORY_CONTRACTS = "factoryContracts";

    static DescriptorMetadata create(String moduleName, String location, Hson.Struct service) {
        int version = service.intValue("version", DEFAULT_DESCRIPTOR_VERSION);
        if (version != CURRENT_DESCRIPTOR_VERSION) {
            throw new IllegalStateException("Invalid descriptor version: " + version
                                                    + " for module \"" + moduleName + "\""
                                                    + " loaded from \"" + location + "\", "
                                                    + "expected version: \"" + CURRENT_DESCRIPTOR_VERSION + "\","
                                                    + " descriptor (if available): "
                                                    + service.stringValue(HSON_DESCRIPTOR, "N/A"));
        }

        String type = service.stringValue(HSON_TYPE, REGISTRY_TYPE_CORE);
        TypeName descriptor = service.stringValue(HSON_DESCRIPTOR)
                .map(TypeName::create)
                .orElseThrow(() -> new IllegalStateException("Could not parse service metadata "
                                                                     + " for module \"" + moduleName + "\""
                                                                     + " loaded from \"" + location + "\", "
                                                                     + "missing \"descriptor\" value"));
        double weight = service.doubleValue(HSON_WEIGHT, Weighted.DEFAULT_WEIGHT);
        Set<ResolvedType> contracts = service.stringArray(HSON_CONTRACTS)
                .orElseGet(List::of)
                .stream()
                .map(ResolvedType::create)
                .collect(Collectors.toUnmodifiableSet());

        Set<ResolvedType> factoryContracts = service.stringArray(HSON_FACTORY_CONTRACTS)
                .orElseGet(List::of)
                .stream()
                .map(ResolvedType::create)
                .collect(Collectors.toSet());

        return new DescriptorMetadataImpl(type, weight, descriptor, contracts, factoryContracts);
    }

    @Override
    public Hson.Struct toHson() {
        var builder = Hson.structBuilder();

        if (!registryType.equals(REGISTRY_TYPE_CORE)) {
            builder.set(HSON_TYPE, registryType);
        }
        if (weight != Weighted.DEFAULT_WEIGHT) {
            builder.set(HSON_WEIGHT, weight);
        }
        builder.set(HSON_DESCRIPTOR, descriptorType.fqName());
        builder.setStrings(HSON_CONTRACTS, contracts.stream()
                .map(ResolvedType::resolvedName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toUnmodifiableList()));
        if (!factoryContracts.isEmpty()) {
            builder.setStrings(HSON_FACTORY_CONTRACTS, factoryContracts.stream()
                    .map(ResolvedType::resolvedName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toUnmodifiableList()));
        }

        return builder.build();
    }
}
