/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import io.helidon.common.types.TypeNames;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ClassModelTest {
    private static final String EXPECTED_AT_SIGN_IN_TEXT = """
            package io.helidon.codegen.classmodel;

            import java.util.List;
            import java.util.Optional;
            
            public class Zavinac {
            
              public void create() {
                service(List.of("@default"), Optional.class)
              }

            }
            """;

    @Test
    void testAtSignInText() throws IOException {
        var m = Method.builder()
                .name("create")
                .addContent("service(")
                .addContent(TypeNames.LIST)
                .addContent(".of(\"@default\")")
                .addContent(", ")
                .addContent(TypeNames.OPTIONAL)
                .addContent(".class")
                .addContent(")")
                .build();
        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("io.helidon.codegen.classmodel")
                .name("Zavinac")
                .addMethod(m)
                .build()
                .write(sw, "  ");

        assertThat(sw.toString(), is(EXPECTED_AT_SIGN_IN_TEXT));
    }
}