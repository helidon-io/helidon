/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

public class AnnotatedMethodListTest {

    @Test
    public void shouldFindAllDeclaredMethods() {
        AnnotatedMethodList list = AnnotatedMethodList.create(Stub.class, true);
        List<String> names = list.stream()
                .map(am -> am.method().getName())
                .collect(Collectors.toList());

        assertThat(names, containsInAnyOrder("one", "two", "three", "four", "five"));
    }

    @Test
    public void shouldFindAllMethods() {
        AnnotatedMethodList list = AnnotatedMethodList.create(Stub.class);
        List<String> names = list.stream()
                .map(am -> am.method().getName())
                .collect(Collectors.toList());

        assertThat(names, containsInAnyOrder("one", "two"));
    }

    @Test
    public void shouldIterateAllMethods() {
        AnnotatedMethodList list = AnnotatedMethodList.create(Stub.class, true);
        List<String> names = new ArrayList<>();
        Iterator<AnnotatedMethod> iterator = list.iterator();
        while (iterator.hasNext()) {
            AnnotatedMethod method = iterator.next();
            names.add(method.method().getName());
        }
        assertThat(names, containsInAnyOrder("one", "two", "three", "four", "five"));
    }

    @Test
    public void shouldGetNonePublicMethods() {
        AnnotatedMethodList list = AnnotatedMethodList.create(Stub.class, true);
        AnnotatedMethodList methods = list.isNotPublic();
        List<String> names = methods.stream()
                .map(am -> am.method().getName())
                .collect(Collectors.toList());

        assertThat(names, containsInAnyOrder("three", "four", "five"));
    }

    @Test
    public void shouldGetMethodsWithParameterCount() {
        AnnotatedMethodList list = AnnotatedMethodList.create(Stub.class, true);
        AnnotatedMethodList methods = list.hasParameterCount(2);
        List<String> names = methods.stream()
                .map(am -> am.method().getName())
                .collect(Collectors.toList());

        assertThat(names, containsInAnyOrder("two", "four"));
    }

    @Test
    public void shouldGetMethodsWithWithReturnType() {
        AnnotatedMethodList list = AnnotatedMethodList.create(Stub.class, true);
        AnnotatedMethodList methods = list.hasReturnType(String.class);
        List<String> names = methods.stream()
                .map(am -> am.method().getName())
                .collect(Collectors.toList());

        assertThat(names, containsInAnyOrder("one", "four"));
    }

    @Test
    public void shouldGetMethodsWithWithNamePrefix() {
        AnnotatedMethodList list = AnnotatedMethodList.create(Stub.class, true);
        AnnotatedMethodList methods = list.nameStartsWith("t");
        List<String> names = methods.stream()
                .map(am -> am.method().getName())
                .collect(Collectors.toList());

        assertThat(names, containsInAnyOrder("two", "three"));
    }

    @Test
    public void shouldGetMethodsWithWithAnnotation() {
        AnnotatedMethodList list = AnnotatedMethodList.create(Stub.class, true);
        AnnotatedMethodList methods = list.withAnnotation(Unary.class);
        List<String> names = methods.stream()
                .map(am -> am.method().getName())
                .collect(Collectors.toList());

        assertThat(names, containsInAnyOrder("one", "three"));
    }

    @Test
    public void shouldGetMethodsWithoutAnnotation() {
        AnnotatedMethodList list = AnnotatedMethodList.create(Stub.class, true);
        AnnotatedMethodList methods = list.withoutAnnotation(Unary.class);
        List<String> names = methods.stream()
                .map(am -> am.method().getName())
                .collect(Collectors.toList());

        assertThat(names, containsInAnyOrder("two", "four", "five"));
    }

    @Test
    public void shouldGetMethodsWithMetaAnnotation() {
        AnnotatedMethodList list = AnnotatedMethodList.create(Stub.class, true);
        AnnotatedMethodList methods = list.withMetaAnnotation(GrpcMethod.class);
        List<String> names = methods.stream()
                .map(am -> am.method().getName())
                .collect(Collectors.toList());

        assertThat(names, containsInAnyOrder("one", "two", "three", "five"));
    }

    @Test
    public void shouldGetMethodsWithoutMetaAnnotation() {
        AnnotatedMethodList list = AnnotatedMethodList.create(Stub.class, true);
        AnnotatedMethodList methods = list.withoutMetaAnnotation(GrpcMethod.class);
        List<String> names = methods.stream()
                .map(am -> am.method().getName())
                .collect(Collectors.toList());

        assertThat(names, containsInAnyOrder("four"));
    }

    /**
     * A stub class to test annotation processing.
     */
    public class Stub {
        @Unary
        public String one(int a) {
            return "";
        }

        @ClientStreaming
        public Long two(int a, int b) {
            return 0L;
        }

        @Unary
        private void three(int a) {
        }

        @RequestType(String.class)
        protected String four(int a, int b) {
            return "";
        }

        @ClientStreaming
        void five() {
        }
    }
}
