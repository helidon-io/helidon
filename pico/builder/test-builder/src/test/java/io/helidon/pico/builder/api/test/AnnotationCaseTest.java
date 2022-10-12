/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.api.test;

import java.util.Arrays;

import io.helidon.pico.builder.test.testsubjects.AnnotationCase;
import io.helidon.pico.builder.test.testsubjects.DefaultAnnotationCaseExt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AnnotationCaseTest {

    @Test
    public void testIt() {
        DefaultAnnotationCaseExt annotationCase = DefaultAnnotationCaseExt.builder().build();
        assertEquals(AnnotationCase.class, annotationCase.annotationType());
        assertSame(annotationCase, annotationCase.get());
        assertEquals("hello", annotationCase.value());
        assertEquals("[a, b, c]", Arrays.asList(annotationCase.strArr()).toString());
    }

}
