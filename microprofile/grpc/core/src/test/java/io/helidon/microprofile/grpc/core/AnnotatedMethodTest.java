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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import javax.inject.Inject;
import javax.inject.Named;

import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AnnotatedMethodTest {

    @Test
    public void shouldNotAllowNullMethod() {
        assertThrows(NullPointerException.class, () -> AnnotatedMethod.create(null));
    }

    @Test
    public void shouldHaveSameDeclaredAndActualAnnotatedMethodsIfNoSuperClass() throws Exception {
        Method method = GrandParent.class.getDeclaredMethod("one");
        AnnotatedMethod annotatedMethod = AnnotatedMethod.create(method);

        assertThat(annotatedMethod.declaredMethod(), is(sameInstance(method)));
        assertThat(annotatedMethod.method(), is(method));
    }

    @Test
    public void shouldHaveSameDeclaredAndActualNonAnnotatedMethodsIfNoSuperClass() throws Exception {
        Method method = GrandParent.class.getDeclaredMethod("two");
        AnnotatedMethod annotatedMethod = AnnotatedMethod.create(method);

        assertThat(annotatedMethod.declaredMethod(), is(sameInstance(method)));
        assertThat(annotatedMethod.method(), is(method));
    }

    @Test
    public void shouldHaveAnnotatedMethodFromSuperClass() throws Exception {
        Method declaredMethod = Parent.class.getDeclaredMethod("one");
        Method method = GrandParent.class.getDeclaredMethod("one");
        AnnotatedMethod annotatedMethod = AnnotatedMethod.create(declaredMethod);

        assertThat(annotatedMethod.declaredMethod(), is(sameInstance(declaredMethod)));
        assertThat(annotatedMethod.method(), is(method));
    }

    @Test
    public void shouldHaveNonAnnotatedMethodOverridingSuperClass() throws Exception {
        Method declaredMethod = Parent.class.getDeclaredMethod("two");
        AnnotatedMethod annotatedMethod = AnnotatedMethod.create(declaredMethod);

        assertThat(annotatedMethod.declaredMethod(), is(sameInstance(declaredMethod)));
        assertThat(annotatedMethod.method(), is(declaredMethod));
    }

    @Test
    public void shouldHaveAnnotatedMethodOverridingAnnotatedMethodInSuperClass() throws Exception {
        Method declaredMethod = Parent.class.getDeclaredMethod("three");
        AnnotatedMethod annotatedMethod = AnnotatedMethod.create(declaredMethod);

        assertThat(annotatedMethod.declaredMethod(), is(sameInstance(declaredMethod)));
        assertThat(annotatedMethod.method(), is(declaredMethod));
    }

    @Test
    public void shouldHaveAnnotatedMethodNotOverridingMethodInSuperClass() throws Exception {
        Method declaredMethod = Parent.class.getDeclaredMethod("four");
        AnnotatedMethod annotatedMethod = AnnotatedMethod.create(declaredMethod);

        assertThat(annotatedMethod.declaredMethod(), is(sameInstance(declaredMethod)));
        assertThat(annotatedMethod.method(), is(declaredMethod));
    }

    @Test
    public void shouldHaveNonAnnotatedMethodNotOverridingMethodInSuperClass() throws Exception {
        Method declaredMethod = Parent.class.getDeclaredMethod("five");
        AnnotatedMethod annotatedMethod = AnnotatedMethod.create(declaredMethod);

        assertThat(annotatedMethod.declaredMethod(), is(sameInstance(declaredMethod)));
        assertThat(annotatedMethod.method(), is(declaredMethod));
    }

    @Test
    public void shouldHaveAnnotatedMethodFromGrandParent() throws Exception {
        Method declaredMethod = Child.class.getDeclaredMethod("one");
        Method method = GrandParent.class.getDeclaredMethod("one");
        AnnotatedMethod annotatedMethod = AnnotatedMethod.create(declaredMethod);

        assertThat(annotatedMethod.declaredMethod(), is(sameInstance(declaredMethod)));
        assertThat(annotatedMethod.method(), is(method));
    }

    @Test
    public void shouldHaveAnnotatedMethodFromParentNotFromInterface() throws Exception {
        Method declaredMethod = Child.class.getDeclaredMethod("three");
        Method method = Parent.class.getDeclaredMethod("three");
        AnnotatedMethod annotatedMethod = AnnotatedMethod.create(declaredMethod);

        assertThat(annotatedMethod.declaredMethod(), is(sameInstance(declaredMethod)));
        assertThat(annotatedMethod.method(), is(method));
    }

    @Test
    public void shouldHaveAnnotatedMethodFromInterface() throws Exception {
        Method declaredMethod = Child.class.getDeclaredMethod("six");
        Method method = InterfaceOne.class.getDeclaredMethod("six");
        AnnotatedMethod annotatedMethod = AnnotatedMethod.create(declaredMethod);

        assertThat(annotatedMethod.declaredMethod(), is(sameInstance(declaredMethod)));
        assertThat(annotatedMethod.method(), is(method));
    }

    @Test
    public void shouldHaveAnnotatedMethodFromFirstDeclaredInterface() throws Exception {
        Method declaredMethod = Multi.class.getDeclaredMethod("three");
        Method method = InterfaceOne.class.getDeclaredMethod("three");
        AnnotatedMethod annotatedMethod = AnnotatedMethod.create(declaredMethod);

        assertThat(annotatedMethod.declaredMethod(), is(sameInstance(declaredMethod)));
        assertThat(annotatedMethod.method(), is(method));
    }

    @Test
    public void shouldHaveAnnotatedMethodFromSuperInterface() throws Exception {
        Method declaredMethod = Service.class.getDeclaredMethod("three");
        Method method = InterfaceOne.class.getDeclaredMethod("three");
        AnnotatedMethod annotatedMethod = AnnotatedMethod.create(declaredMethod);

        assertThat(annotatedMethod.declaredMethod(), is(sameInstance(declaredMethod)));
        assertThat(annotatedMethod.method(), is(method));
    }

    @Test
    public void shouldHaveAnnotatedMethodFromParentsInterface() throws Exception {
        Method declaredMethod = Child.class.getDeclaredMethod("seven");
        Method method = InterfaceFour.class.getDeclaredMethod("seven");
        AnnotatedMethod annotatedMethod = AnnotatedMethod.create(declaredMethod);

        assertThat(annotatedMethod.declaredMethod(), is(sameInstance(declaredMethod)));
        assertThat(annotatedMethod.method(), is(method));
    }

    @Test
    public void shouldMergeAnnotations() throws Exception {
        Method declaredMethod = Parent.class.getDeclaredMethod("one");
        AnnotatedMethod annotatedMethod = AnnotatedMethod.create(declaredMethod);
        Annotation[] annotations = annotatedMethod.getAnnotations();

        assertThat(annotations.length, is(3));
        assertThat(annotatedMethod.getAnnotation(Inject.class), is(notNullValue()));
        assertThat(annotatedMethod.getAnnotation(Named.class), is(notNullValue()));
        assertThat(annotatedMethod.getAnnotation(GrpcMethod.class), is(notNullValue()));
    }

    @GrpcService
    public static class GrandParent {

        @GrpcMethod(type = MethodDescriptor.MethodType.UNARY)
        @Inject
        public void one() {
        }

        public void two() {
        }

        @GrpcMethod(type = MethodDescriptor.MethodType.UNARY)
        public void three() {
        }
    }

    public static class Parent
            extends GrandParent
            implements InterfaceFour {

        @Override
        @Named("bar")
        public void one() {
        }

        @Override
        public void two() {
        }

        @GrpcMethod(type = MethodDescriptor.MethodType.UNARY)
        @Override
        public void three() {
        }

        @GrpcMethod(type = MethodDescriptor.MethodType.UNARY)
        public void four() {
        }

        public void five() {
        }

        @Override
        public void seven() {
        }
    }

    public static class Child
            extends Parent
            implements InterfaceOne {
        @Override
        public void one() {
        }

        @Override
        public void three() {
        }

        public void six() {
        }

        @Override
        public void seven() {
        }
    }

    public interface InterfaceOne {
        @GrpcMethod(type = MethodDescriptor.MethodType.UNARY)
        void three();

        @GrpcMethod(type = MethodDescriptor.MethodType.UNARY)
        void six();
    }

    public interface InterfaceTwo {
        @GrpcMethod(type = MethodDescriptor.MethodType.UNARY)
        void three();
    }

    public interface InterfaceThree
            extends InterfaceOne {
        @GrpcMethod(type = MethodDescriptor.MethodType.UNARY)
        void six();
    }

    public interface InterfaceFour {
        @GrpcMethod(type = MethodDescriptor.MethodType.UNARY)
        void seven();
    }

    public static class Multi
            implements InterfaceOne, InterfaceTwo {
        @Override
        public void three() {
        }

        @Override
        public void six() {
        }
    }

    public static class Service
            implements InterfaceThree {
        @Override
        public void three() {
        }

        @Override
        public void six() {
        }
    }
}
