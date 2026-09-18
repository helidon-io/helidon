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

import java.util.HashSet;
import java.util.Set;

import io.helidon.codegen.ClassCode;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DescriptorClassCodeTest {
    private static final ResolvedType CONTRACT = ResolvedType.create("example.Contract");
    private static final ResolvedType FACTORY_CONTRACT = ResolvedType.create("example.Factory");

    @Test
    void rejectsNullArguments() {
        var classCode = classCode();

        assertThrows(NullPointerException.class, () -> DescriptorClassCode.create(null, 1, Set.of(), Set.of()));
        assertThrows(NullPointerException.class, () -> DescriptorClassCode.create(classCode, 1, null, Set.of()));
        assertThrows(NullPointerException.class, () -> DescriptorClassCode.create(classCode, 1, Set.of(), null));
    }

    @Test
    void rejectsNullContracts() {
        var classCode = classCode();
        Set<ResolvedType> contracts = new HashSet<>();
        contracts.add(null);

        assertThrows(NullPointerException.class, () -> DescriptorClassCode.create(classCode, 1, contracts, Set.of()));
        assertThrows(NullPointerException.class, () -> DescriptorClassCode.create(classCode, 1, Set.of(), contracts));
    }

    @Test
    void copiesContractSets() {
        var classCode = classCode();
        Set<ResolvedType> contracts = new HashSet<>(Set.of(CONTRACT));
        Set<ResolvedType> factoryContracts = new HashSet<>(Set.of(FACTORY_CONTRACT));
        var descriptor = DescriptorClassCode.create(classCode, 1, contracts, factoryContracts);

        contracts.clear();
        factoryContracts.clear();

        assertThat(descriptor.classCode(), is(classCode));
        assertThat(descriptor.weight(), is(1.0));
        assertThat(descriptor.contracts(), is(Set.of(CONTRACT)));
        assertThat(descriptor.factoryContracts(), is(Set.of(FACTORY_CONTRACT)));
        assertThrows(UnsupportedOperationException.class, () -> descriptor.contracts().clear());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.factoryContracts().clear());
    }

    private static ClassCode classCode() {
        return new ClassCode(TypeName.create("example.ServiceDescriptor"),
                             ClassModel.builder(),
                             TypeName.create("example.Service"));
    }
}
