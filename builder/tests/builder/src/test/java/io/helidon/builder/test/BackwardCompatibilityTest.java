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

import java.util.Optional;

import io.helidon.builder.test.testsubjects.A;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/*
Make sure the A from ABlueprint can be extended without implementing the default method
 */
class BackwardCompatibilityTest {
    @Test
    void testDefaultMethodValue() {
        A a = A.builder()
                .a("hello")
                .build();

        assertThat(a.a(), is("hello"));
        assertThat(a.aNewProperty(), is(Optional.empty()));
    }

    @Test
    void testFoo() {
        // make sure the class compiles and the default method on `A` is truly default
        Foo foo = new Foo();

        assertThat(foo.a(), is(""));
    }

    // this class must compile, as a() should be the only non-default method
    static class Foo implements A {
        @Override
        public String a() {
            return "";
        }
    }
}
