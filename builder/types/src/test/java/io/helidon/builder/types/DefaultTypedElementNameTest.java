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

package io.helidon.builder.types;

import org.junit.jupiter.api.Test;

import static io.helidon.builder.types.DefaultTypeName.create;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DefaultTypedElementNameTest {

    @Test
    void declarations() {
        assertThat(DefaultTypedElementName.builder()
                           .elementName("arg")
                           .typeName(create(boolean.class))
                           .build().toString(),
                   is("boolean arg"));
        assertThat(DefaultTypedElementName.builder()
                           .elementName("arg")
                           .typeName(create(byte.class))
                           .build().toString(),
                   is("byte arg"));
        assertThat(DefaultTypedElementName.builder()
                           .elementName("arg")
                           .typeName(create(short.class))
                           .build().toString(),
                   is("short arg"));
        assertThat(DefaultTypedElementName.builder()
                           .elementName("arg")
                           .typeName(create(int.class))
                           .build().toString(),
                   is("int arg"));
        assertThat(DefaultTypedElementName.builder()
                           .elementName("arg")
                           .typeName(create(long.class))
                           .build().toString(),
                   is("long arg"));
        assertThat(DefaultTypedElementName.builder()
                           .elementName("arg")
                           .typeName(create(char.class))
                           .build().toString(),
                   is("char arg"));
        assertThat(DefaultTypedElementName.builder()
                           .elementName("arg")
                           .typeName(create(float.class))
                           .build().toString(),
                   is("float arg"));
        assertThat(DefaultTypedElementName.builder()
                           .elementName("arg")
                           .typeName(create(double.class))
                           .build().toString(),
                   is("double arg"));
        assertThat(DefaultTypedElementName.builder()
                           .elementName("arg")
                           .typeName(create(void.class))
                           .build().toString(),
                   is("void arg"));
    }

}
