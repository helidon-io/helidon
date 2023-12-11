/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.codegen.classmodel;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TypeTest {
    @Test
    void testPlainType() throws IOException {
        assertThat(write(TypeNames.STRING), is("java.lang.String"));
    }

    @Test
    void testGenericType() throws IOException {
        assertThat(write(TypeName.builder(TypeNames.LIST)
                                 .addTypeArgument(TypeNames.STRING)
                                 .build()), is("java.util.List<java.lang.String>"));
    }

    @Test
    void testNestedGenericType() throws IOException {
        assertThat(write(TypeName.builder(TypeNames.LIST)
                                 .addTypeArgument(TypeName.builder(TypeNames.SUPPLIER)
                                                          .addTypeArgument(TypeNames.STRING)
                                                          .build())
                                 .build()), is("java.util.List<java.util.function.Supplier<java.lang.String>>"));
    }

    @Test
    void testWildcardType() throws IOException {
        assertThat(write(TypeName.builder(TypeNames.SUPPLIER)
                                 .addTypeArgument(TypeName.builder(TypeName.create(
                                                 "io.helidon.inject.api.InjectionPointInfo"))
                                                          .wildcard(true)
                                                          .build())
                                 .build()),
                   is("java.util.function.Supplier<? extends io.helidon.inject.api.InjectionPointInfo>"));
    }

    private String write(TypeName typeName) throws IOException {
        Type classModelType = Type.fromTypeName(typeName);
        StringWriter stringWriter = new StringWriter();
        ModelWriter modelWriter = new ModelWriter(stringWriter, "");
        classModelType.writeComponent(modelWriter, Set.of(), ImportOrganizer.builder()
                .packageName("io.helidon.tests")
                .typeName("MyType")
                .build(), ClassType.CLASS);

        return stringWriter.toString();
    }
}
