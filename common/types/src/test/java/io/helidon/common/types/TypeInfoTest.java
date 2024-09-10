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

package io.helidon.common.types;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class TypeInfoTest {
    @Test
    void testFindInHierarchyInterfaces() {
        TypeName ifaceA = TypeName.create("io.helidon.common.types.test.A");
        TypeName ifaceB = TypeName.create("io.helidon.common.types.test.B");
        TypeName ifaceC = TypeName.create("io.helidon.common.types.test.C");
        TypeInfo aInfo = TypeInfo.builder()
                .typeName(ifaceA)
                .kind(ElementKind.INTERFACE)
                .build();
        TypeInfo bInfo = TypeInfo.builder()
                .typeName(ifaceB)
                .kind(ElementKind.INTERFACE)
                .addInterfaceTypeInfo(aInfo)
                .build();
        TypeInfo cInfo = TypeInfo.builder()
                .typeName(ifaceC)
                .kind(ElementKind.INTERFACE)
                .addInterfaceTypeInfo(bInfo)
                .build();

        Optional<TypeInfo> foundInfo = cInfo.findInHierarchy(ifaceA);
        assertThat(foundInfo, not(Optional.empty()));
        assertThat(foundInfo.get(), sameInstance(aInfo));

        foundInfo = cInfo.findInHierarchy(ifaceB);
        assertThat(foundInfo, not(Optional.empty()));
        assertThat(foundInfo.get(), sameInstance(bInfo));

        foundInfo = bInfo.findInHierarchy(ifaceA);
        assertThat(foundInfo, not(Optional.empty()));
        assertThat(foundInfo.get(), sameInstance(aInfo));

        foundInfo = aInfo.findInHierarchy(ifaceB);
        assertThat(foundInfo, is(Optional.empty()));
    }

    @Test
    void testFindInHierarchyTypes() {
        TypeName ifaceA = TypeName.create("io.helidon.common.types.test.A");
        TypeName classB = TypeName.create("io.helidon.common.types.test.B");
        TypeName classC = TypeName.create("io.helidon.common.types.test.C");
        TypeInfo aInfo = TypeInfo.builder()
                .typeName(ifaceA)
                .kind(ElementKind.INTERFACE)
                .build();
        TypeInfo bInfo = TypeInfo.builder()
                .typeName(classB)
                .kind(ElementKind.CLASS)
                .addInterfaceTypeInfo(aInfo)
                .build();
        TypeInfo cInfo = TypeInfo.builder()
                .typeName(classC)
                .kind(ElementKind.INTERFACE)
                .superTypeInfo(bInfo)
                .build();

        Optional<TypeInfo> foundInfo = cInfo.findInHierarchy(ifaceA);
        assertThat(foundInfo, not(Optional.empty()));
        assertThat(foundInfo.get(), sameInstance(aInfo));

        foundInfo = cInfo.findInHierarchy(classB);
        assertThat(foundInfo, not(Optional.empty()));
        assertThat(foundInfo.get(), sameInstance(bInfo));

        foundInfo = bInfo.findInHierarchy(ifaceA);
        assertThat(foundInfo, not(Optional.empty()));
        assertThat(foundInfo.get(), sameInstance(aInfo));

        foundInfo = aInfo.findInHierarchy(classB);
        assertThat(foundInfo, is(Optional.empty()));
    }

}
