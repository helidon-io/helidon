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

package io.helidon.json.codegen;

import java.util.List;

import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class JsonConverterGeneratorTest {

    @Test
    void genericTypeNameDoesNotUseToStringForFieldSuffix() {
        TypeName person = TypeName.create("io.example.MyTestPerson");
        TypeName builderBase = TypeName.builder()
                .packageName("io.example")
                .className("BuilderBase")
                .addTypeArgument(person)
                .build();

        // toString()/resolvedName() keeps generic syntax, which used to leak into field names
        assertThat(builderBase.toString(), containsString("MyTestPerson>"));

        String suffix = JsonConverterGenerator.ensureUpperStart(builderBase);
        assertThat(suffix, is("BuilderBaseMyTestPerson"));
        assertThat(suffix, not(containsString("<")));
        assertThat(suffix, not(containsString(">")));
        assertThat(Character.isJavaIdentifierStart(suffix.charAt(0)), is(true));
        for (int i = 0; i < suffix.length(); i++) {
            assertThat(Character.isJavaIdentifierPart(suffix.charAt(i)), is(true));
        }
    }

    @Test
    void arrayAndNestedTypesRemainValidIdentifiers() {
        TypeName stringArray = TypeName.create(String[].class);
        assertThat(JsonConverterGenerator.ensureUpperStart(stringArray), is("StringArray"));

        TypeName inner = TypeName.builder()
                .packageName("io.example")
                .className("Inner")
                .enclosingNames(List.of("Outer"))
                .build();
        assertThat(JsonConverterGenerator.ensureUpperStart(inner), is("OuterInner"));
    }
}
