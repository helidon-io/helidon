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

package io.helidon.codegen;

import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CodegenValidatorTest {
    private static final TypeName ANNOTATION_TYPE = TypeName.create("io.helidon.test.MyAnnotation");
    private static final TypedElementInfo ELEMENT = TypedElementInfo.builder()
            .kind(ElementKind.METHOD)
            .elementName("method")
            .typeName(TypeNames.PRIMITIVE_VOID)
            .build();

    @Test
    public void testValidUri() {
        String expected = "https://www.example.com/test";
        String actual = CodegenValidator.validateUri(TypeName.create(CodegenValidatorTest.class),
                                                     ELEMENT,
                                                     ANNOTATION_TYPE,
                                                     "uriProperty",
                                                     expected);

        assertThat(expected, is(actual));
    }

    @Test
    public void testInvalidUri() {
        String expected = "https://  www\\.example.com/test";
        var exception = assertThrows(CodegenException.class,
                                     () -> CodegenValidator.validateUri(TypeName.create(CodegenValidatorTest.class),
                                                                        ELEMENT,
                                                                        ANNOTATION_TYPE,
                                                                        "uriProperty",
                                                                        expected));

        String message = exception.getMessage();

        assertThat(message, containsString("URI"));
        assertThat(message, containsString("io.helidon.test.MyAnnotation.uriProperty()"));
        assertThat(message, containsString(expected));
    }

    @Test
    public void testValidDuration() {
        String expected = "PT10.1S";
        String actual = CodegenValidator.validateDuration(TypeName.create(CodegenValidatorTest.class),
                                                          ELEMENT,
                                                          ANNOTATION_TYPE,
                                                          "durationProperty",
                                                          expected);

        assertThat(expected, is(actual));
    }

    @Test
    public void testInvalidDuration() {
        String expected = "10 minutes";
        var exception = assertThrows(CodegenException.class,
                                     () -> CodegenValidator.validateDuration(TypeName.create(CodegenValidatorTest.class),
                                                                             ELEMENT,
                                                                             ANNOTATION_TYPE,
                                                                             "durationProperty",
                                                                             expected));

        String message = exception.getMessage();

        assertThat(message, containsString("Duration"));
        assertThat(message, containsString("io.helidon.test.MyAnnotation.durationProperty()"));
        assertThat(message, containsString(expected));
    }
}
