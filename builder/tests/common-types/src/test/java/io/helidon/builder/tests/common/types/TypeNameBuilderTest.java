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

package io.helidon.builder.tests.common.types;

import java.util.Optional;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class TypeNameBuilderTest {
    @Test
    void preservesEnclosingMetadataWhenCopied() {
        Annotation annotation = Annotation.create(TypeName.create("example.Marker"));
        TypeName enclosing = TypeName.builder(TypeName.create("example.Outer"))
                .addAnnotation(annotation)
                .addTypeArgument(TypeName.create(String.class))
                .build();
        TypeName raw = TypeName.create("example.Outer.Inner");
        TypeName type = TypeName.builder(raw)
                .enclosingType(enclosing)
                .build();
        TypeName copy = TypeName.builder(type).build();

        assertThat(copy.enclosingType().orElseThrow().annotations(), contains(annotation));
        assertThat(copy.enclosingType().orElseThrow().typeArguments(), contains(TypeName.create(String.class)));
        assertThat(copy.annotations().isEmpty(), is(true));
        assertThat(copy.enclosingNames(), is(raw.enclosingNames()));
        assertThat(copy.name(), is(raw.name()));
        assertThat(copy.resolvedName(), is(raw.resolvedName()));
        assertThat(copy, is(raw));
        assertThat(copy.hashCode(), is(raw.hashCode()));
        assertThat(copy.compareTo(raw), is(0));
        assertThat(TypeName.builder(copy).clearEnclosingType().build().enclosingType(), is(Optional.empty()));
    }
}
