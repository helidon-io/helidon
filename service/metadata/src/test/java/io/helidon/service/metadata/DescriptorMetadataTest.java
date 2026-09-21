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

package io.helidon.service.metadata;

import java.util.HashSet;
import java.util.Set;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DescriptorMetadataTest {
    private static final TypeName DESCRIPTOR = TypeName.create("example.ServiceDescriptor");
    private static final ResolvedType CONTRACT = ResolvedType.create("example.Contract");
    private static final ResolvedType FACTORY_CONTRACT = ResolvedType.create("example.Factory");

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> DescriptorMetadata.create(null, 1, Set.of(), Set.of()));
        assertThrows(NullPointerException.class, () -> DescriptorMetadata.create(DESCRIPTOR, 1, null, Set.of()));
        assertThrows(NullPointerException.class, () -> DescriptorMetadata.create(DESCRIPTOR, 1, Set.of(), null));
    }

    @Test
    void rejectsNullContracts() {
        Set<ResolvedType> contracts = new HashSet<>();
        contracts.add(null);

        assertThrows(NullPointerException.class, () -> DescriptorMetadata.create(DESCRIPTOR, 1, contracts, Set.of()));
        assertThrows(NullPointerException.class, () -> DescriptorMetadata.create(DESCRIPTOR, 1, Set.of(), contracts));
    }

    @Test
    void copiesContractSets() {
        Set<ResolvedType> contracts = new HashSet<>(Set.of(CONTRACT));
        Set<ResolvedType> factoryContracts = new HashSet<>(Set.of(FACTORY_CONTRACT));
        var metadata = DescriptorMetadata.create(DESCRIPTOR, 1, contracts, factoryContracts);

        contracts.clear();
        factoryContracts.clear();

        assertThat(metadata.descriptorType(), is(DESCRIPTOR));
        assertThat(metadata.weight(), is(1.0));
        assertThat(metadata.contracts(), is(Set.of(CONTRACT)));
        assertThat(metadata.factoryContracts(), is(Set.of(FACTORY_CONTRACT)));
        assertThrows(UnsupportedOperationException.class, () -> metadata.contracts().clear());
        assertThrows(UnsupportedOperationException.class, () -> metadata.factoryContracts().clear());
    }
}
