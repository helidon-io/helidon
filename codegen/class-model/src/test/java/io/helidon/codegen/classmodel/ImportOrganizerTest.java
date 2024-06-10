/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;

class ImportOrganizerTest {
    @Test
    void testImportSystemLoggerLevel() throws IOException {
        TypeName typeNameLevel = TypeName.create(System.Logger.Level.class);
        assertThat(typeNameLevel.className(), is("Level"));
        assertThat(typeNameLevel.enclosingNames(), hasItems("System", "Logger"));
        assertThat(typeNameLevel.packageName(), is("java.lang"));

        Type type = Type.fromTypeName(typeNameLevel);
        assertThat(type.packageName(), is("java.lang"));
        assertThat(type.declaringClass(), is(Optional.of(Type.fromTypeName(TypeName.create(System.Logger.class)))));
        assertThat(type.innerClass(), is(true));

        ImportOrganizer io = ImportOrganizer.builder()
                .typeName("io.helidon.NotImportant")
                .packageName("io.helidon")
                .addImport(type)
                .build();
        StringWriter writer = new StringWriter();
        ModelWriter modelWriter = new ModelWriter(writer, "");
        type.writeComponent(modelWriter, Set.of(), io, ClassType.CLASS);

        String written = writer.toString();
        assertThat(written, is("System.Logger.Level"));

        List<String> imports = io.imports();
        assertThat(imports, empty());
    }
}