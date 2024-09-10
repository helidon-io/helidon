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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class SignatureTest {

    @Test
    void testMethodSignature() {
        TypedElementInfo m1 = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName("method")
                .typeName(TypeNames.STRING)
                .build();

        TypedElementInfo m2 = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName("method")
                .typeName(TypeNames.STRING)
                .build();

        TypedElementInfo m3 = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName("method2")
                .typeName(TypeNames.STRING)
                .build();

        TypedElementInfo m4 = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName("method")
                .typeName(TypeNames.STRING)
                .parameterArguments(List.of(TypedElementInfo.builder()
                                                    .kind(ElementKind.PARAMETER)
                                                    .typeName(TypeNames.STRING)
                                                    .elementName("param1")
                                                    .buildPrototype()))
                .build();

        ElementSignature s1 = m1.signature();
        ElementSignature s2 = m2.signature();
        ElementSignature s3 = m3.signature();
        ElementSignature s4 = m4.signature();

        // this is specified in Javadoc and must not be changed
        assertThat(s1.text(), is("method()"));
        assertThat(s2.text(), is("method()"));
        assertThat(s3.text(), is("method2()"));
        assertThat(s4.text(), is("method(java.lang.String)"));

        assertThat(s1, is(s2));
        assertThat(s1, not(s3));
        assertThat(s1, not(s4));

        assertThat(s1.hashCode(), is(s2.hashCode()));

        assertThat(s1.name(), is("method"));
        assertThat(s1.type(), is(TypeNames.STRING));
        assertThat(s1.parameterTypes(), is(List.of()));
    }

    @Test
    void testConstructorSignature() {
        TypedElementInfo m1 = TypedElementInfo.builder()
                .kind(ElementKind.CONSTRUCTOR)
                .typeName(TypeNames.STRING)
                .build();

        TypedElementInfo m2 = TypedElementInfo.builder()
                .kind(ElementKind.CONSTRUCTOR)
                .typeName(TypeNames.STRING)
                .build();

        TypedElementInfo m3 = TypedElementInfo.builder()
                .kind(ElementKind.CONSTRUCTOR)
                .typeName(TypeNames.STRING)
                .parameterArguments(List.of(TypedElementInfo.builder()
                                                    .kind(ElementKind.PARAMETER)
                                                    .typeName(TypeNames.STRING)
                                                    .elementName("param1")
                                                    .buildPrototype()))
                .build();

        ElementSignature s1 = m1.signature();
        ElementSignature s2 = m2.signature();
        ElementSignature s3 = m3.signature();

        // this is specified in Javadoc and must not be changed
        assertThat(s1.text(), is("()"));
        assertThat(s2.text(), is("()"));
        assertThat(s3.text(), is("(java.lang.String)"));

        assertThat(s1, is(s2));
        assertThat(s1, not(s3));

        assertThat(s1.hashCode(), is(s2.hashCode()));

        assertThat(s1.name(), is("<init>"));
        assertThat(s1.type(), is(TypeNames.PRIMITIVE_VOID));
        assertThat(s1.parameterTypes(), is(List.of()));
    }

    @Test
    void testFieldSignature() {
        TypedElementInfo m1 = TypedElementInfo.builder()
                .kind(ElementKind.FIELD)
                .elementName("field")
                .typeName(TypeNames.STRING)
                .build();

        TypedElementInfo m2 = TypedElementInfo.builder()
                .kind(ElementKind.FIELD)
                .elementName("field")
                .typeName(TypeNames.STRING)
                .build();

        TypedElementInfo m3 = TypedElementInfo.builder()
                .kind(ElementKind.FIELD)
                .elementName("field2")
                .typeName(TypeNames.STRING)
                .build();

        ElementSignature s1 = m1.signature();
        ElementSignature s2 = m2.signature();
        ElementSignature s3 = m3.signature();

        // this is specified in Javadoc and must not be changed
        assertThat(s1.text(), is("field"));
        assertThat(s2.text(), is("field"));
        assertThat(s3.text(), is("field2"));

        assertThat(s1, is(s2));
        assertThat(s1, not(s3));

        assertThat(s1.hashCode(), is(s2.hashCode()));

        assertThat(s1.name(), is("field"));
        assertThat(s1.type(), is(TypeNames.STRING));
        assertThat(s1.parameterTypes(), is(List.of()));
    }
}
