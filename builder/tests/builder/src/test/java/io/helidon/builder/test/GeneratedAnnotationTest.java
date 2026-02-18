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

package io.helidon.builder.test;

import io.helidon.builder.api.Prototype;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class GeneratedAnnotationTest {

    @Test
    void testGeneratedAnnotation() {
        Deprecated annotation = PrototypeAnnotated.class.getAnnotation(Deprecated.class);
        assertThat(annotation, notNullValue());
        assertThat(annotation.forRemoval(), is(true));
        assertThat(annotation.since(), is("4.4.0"));
    }

    @Prototype.Blueprint
    @Prototype.Annotated("java.lang.Deprecated(forRemoval = true, since = \"4.4.0\")")
    interface PrototypeAnnotatedBlueprint {
    }

}
