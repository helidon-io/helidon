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

package io.helidon.builder.test;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.builder.test.testsubjects.Generics;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class GenericTest {

    @Test
    void genericsTest() {
        Generics.Builder<ImplOfT, ImplOfT> builder = Generics.builder();
        builder.addTValue(new ImplOfT("firstTValue"));
        builder.addTValue(new ImplOfT("secondTValue"));
        builder.addXValue(new ImplOfT("firstXValue"));
        builder.addXValue(new ImplOfT("secondXValue"));
        builder.putMappedValue(new ImplOfT("key"), new ImplOfT("value"));
        builder.putMappedValue(new ImplOfT("key2"), new ImplOfT("value2"));
        builder.complicatedValue(new Supply());

        Generics<ImplOfT, ImplOfT> generics = builder.build();

        assertThat(generics.tValues(), hasItems(new ImplOfT("firstTValue"), new ImplOfT("secondTValue")));
        assertThat(generics.xValues(), hasItems(new ImplOfT("firstXValue"), new ImplOfT("secondXValue")));
        assertThat(generics.complicatedValue(), not(Optional.empty()));
        assertThat(generics.complicatedValue().get().get(), is(new ImplOfT("supplied")));
        assertThat(generics.mappedValues().size(), is(2));
    }

    private static class Supply implements Supplier<ImplOfT> {
        @Override
        public ImplOfT get() {
            return new ImplOfT("supplied");
        }
    }
    private static class ImplOfT implements CharSequence, Serializable {
        private final String delegate;

        private ImplOfT(String delegate) {
            this.delegate = delegate;
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public char charAt(int index) {
            return delegate.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return delegate.subSequence(start, end);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ImplOfT implOfT)) {
                return false;
            }
            return Objects.equals(delegate, implOfT.delegate);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(delegate);
        }
    }
}
