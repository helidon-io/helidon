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

package io.helidon.security.providers.common.spi;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class AnnotationAnalyzerTest {

    @Test
    void testLegacyAnalyzerWithGenericMethodOverloads() throws NoSuchMethodException {
        LegacyAnalyzer analyzer = new LegacyAnalyzer();

        for (Class<?> parameterType : List.<Class<?>>of(Number.class, CharSequence.class)) {
            Method method = GenericResource.class.getDeclaredMethod("endpoint", parameterType);
            TypedElementInfo methodInfo = createMethodInfo(method);

            analyzer.analyze(TypeName.create(GenericResource.class),
                             methodInfo,
                             AnnotationAnalyzer.AnalyzerResponse.abstain());

            assertThat(analyzer.analyzedMethod, is(method));
        }
    }

    @Test
    void testLegacyAnalyzerWithCovariantBridgeMethods() {
        for (String methodName : List.of("first", "second")) {
            List<Method> methods = Arrays.stream(CovariantResource.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .toList();
            Method method = methods.stream()
                    .filter(candidate -> !candidate.isBridge())
                    .findFirst()
                    .orElseThrow();
            assertThat(methods.stream().filter(Method::isBridge).toList(), hasSize(1));

            TypedElementInfo methodInfo = createMethodInfo(method);
            LegacyAnalyzer originatingAnalyzer = new LegacyAnalyzer();
            originatingAnalyzer.analyze(TypeName.create(CovariantResource.class),
                                        TypedElementInfo.builder(methodInfo).originatingElement(method).build(),
                                        AnnotationAnalyzer.AnalyzerResponse.abstain());
            assertThat(originatingAnalyzer.analyzedMethod, is(method));

            LegacyAnalyzer fallbackAnalyzer = new LegacyAnalyzer();
            fallbackAnalyzer.analyze(TypeName.create(CovariantResource.class),
                                     methodInfo,
                                     AnnotationAnalyzer.AnalyzerResponse.abstain());
            assertThat(fallbackAnalyzer.analyzedMethod, is(method));
        }
    }

    private static TypedElementInfo createMethodInfo(Method method) {
        var builder = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .enclosingType(TypeName.create(method.getDeclaringClass()))
                .elementName(method.getName())
                .typeName(TypeName.create(method.getGenericReturnType()));
        for (int i = 0; i < method.getGenericParameterTypes().length; i++) {
            int parameterIndex = i;
            builder.addParameterArgument(parameter -> parameter
                    .kind(ElementKind.PARAMETER)
                    .elementName("value" + parameterIndex)
                    .typeName(TypeName.create(method.getGenericParameterTypes()[parameterIndex])));
        }
        return builder.build();
    }

    private interface GenericContract<T> {

        T first();

        T second();
    }

    private static final class GenericResource {

        <T extends Number> String endpoint(T value) {
            return String.valueOf(value);
        }

        <T extends CharSequence> String endpoint(T value) {
            return String.valueOf(value);
        }
    }

    private static final class CovariantResource implements GenericContract<String> {

        @Override
        public String first() {
            return "first";
        }

        @Override
        public String second() {
            return "second";
        }
    }

    private static final class LegacyAnalyzer implements AnnotationAnalyzer {
        private Method analyzedMethod;

        @Override
        public AnalyzerResponse analyze(Class<?> maybeAnnotated) {
            return AnalyzerResponse.abstain();
        }

        @Override
        @SuppressWarnings("removal")
        public AnalyzerResponse analyze(Method maybeAnnotated, AnalyzerResponse previousResponse) {
            analyzedMethod = maybeAnnotated;
            return AnalyzerResponse.abstain(previousResponse);
        }
    }
}
