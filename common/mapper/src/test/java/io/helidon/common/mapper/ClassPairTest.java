/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.common.mapper;

import io.helidon.common.mapper.BuiltInMappers.ClassPair;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

// class pair is used as a key for mapping
class ClassPairTest {
    @Test
    void testSameInstance() {
        ClassPair first = new ClassPair(TestClass.class, TestClass.class);

        assertThat("Same instance must be equal", first.equals(first), is(true));
    }

    @Test
    void testEqual() {
        ClassPair first = new ClassPair(TestClass.class, TestClass.class);
        ClassPair second = new ClassPair(TestClass.class, TestClass.class);

        assertThat("Must be equal", first.equals(second), is(true));
        assertThat("Must be equal", second.equals(first), is(true));
        assertThat("Same hash code", first.hashCode(), is(second.hashCode()));
    }

    @Test
    void testUnEqual() {
        ClassPair first = new ClassPair(TestClass.class, TestClass.class);
        ClassPair second = new ClassPair(TestClass.class, ClassPairTest.class);

        assertThat("Must be equal", first.equals(second), is(false));
        assertThat("Must be equal", second.equals(first), is(false));
    }

    @Test
    void testDifferentClassWithSameHash() {
        ClassPair first = new ClassPair(TestClass.class, TestClass.class);
        TestClass second = new TestClass(first.hashCode());

        assertThat("Must not be equal", first.equals(second), is(false));
        assertThat("Must not be equal", second.equals(first), is(false));
        assertThat("Same hash code", first.hashCode(), is(second.hashCode()));
    }

    private static class TestClass {
        private final int hashCode;

        private TestClass(int hashCode) {
            this.hashCode = hashCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestClass testClass = (TestClass) o;
            return hashCode == testClass.hashCode;
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }
    }
}
