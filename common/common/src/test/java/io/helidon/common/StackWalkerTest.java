/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Unit test for {@link StackWalker}.
 */
class StackWalkerTest {
    @Test
    void testGetCallerClass() {
        Class<?> clazz = StackWalker.getInstance().getCallerClass();
        assertThat(clazz, not(sameInstance(StackWalkerTest.class)));
        // called by junit
        assertThat(clazz.getName(), startsWith("org.junit"));
    }

    @Test
    void testStackWalking() {
        Optional<StackTraceElement> el = StackWalker.getInstance().walk(Stream::findFirst);

        assertThat(el, not(Optional.empty()));

        StackTraceElement stackTraceElement = el.get();

        assertThat(stackTraceElement.getClassName(), is(StackWalkerTest.class.getName()));
        assertThat(stackTraceElement.getMethodName(), is("testStackWalking"));
        assertThat(stackTraceElement.getFileName(), is(StackWalkerTest.class.getSimpleName() + ".java"));
        assertThat(stackTraceElement.getLineNumber(), greaterThan(0));
    }

    private StackTraceElement getFirst() {
        return StackWalker.getInstance().walk(Stream::findFirst).orElse(null);
    }

    @Test
    void testStackWalking2() {
        StackTraceElement stackTraceElement = getFirst();

        assertThat(stackTraceElement, notNullValue());
        assertThat(stackTraceElement.getClassName(), is(StackWalkerTest.class.getName()));
        assertThat(stackTraceElement.getMethodName(), is("getFirst"));
        assertThat(stackTraceElement.getFileName(), is(StackWalkerTest.class.getSimpleName() + ".java"));
        assertThat(stackTraceElement.getLineNumber(), greaterThan(0));
    }

    @Test
    void testStackWalking3() {
        Optional<StackTraceElement> junit = StackWalker.getInstance()
                .walk(stream -> stream.filter(it -> it.getClassName().startsWith("org.junit.jupiter"))
                        .findFirst());

        assertThat(junit, not(Optional.empty()));
    }
}
