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

package io.helidon.common.types;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class TypeStashTest {

    @Test
    void ensureSameInstance() {
        TypeName first = TypeStash.stash(TypeStashTest.class);
        TypeName second = TypeStash.stash(TypeStashTest.class);

        assertThat(first, is(sameInstance(second)));
    }

    @Test
    void ensureSameInstanceString() {
        String type = "java.util.List";

        TypeName first = TypeStash.stash(type);
        TypeName second = TypeStash.stash(type);

        assertThat(first, is(sameInstance(second)));
    }

    @Test
    void ensureEqualInstanceGenerics() {
        String type = "java.util.List<java.lang.String>";

        TypeName first = TypeStash.stash(type);
        TypeName second = TypeStash.stash(type);

        assertThat(first, is(second));
    }
}
