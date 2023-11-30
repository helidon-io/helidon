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

package io.helidon.builder.processor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.processor.ElementInfoPredicates;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class TypeInfoPredicatesTest {
    private static final TypeInfo TEST_SUBJECT = TypeInfo.builder()
            .typeName(TypeName.create("io.helidon.builder.processor.test.TestSubject"))
            .kind(ElementKind.INTERFACE)
            .accessModifier(AccessModifier.PUBLIC)
            .addElementInfo(it -> it.kind(ElementKind.FIELD)
                    .elementName("privateField")
                    .addElementModifier(Modifier.FINAL)
                    .accessModifier(AccessModifier.PRIVATE)
                    .typeName(Types.STRING_TYPE))
            .addElementInfo(it -> it.kind(ElementKind.FIELD)
                    .elementName("publicField")
                    .addElementModifier(Modifier.FINAL)
                    .accessModifier(AccessModifier.PUBLIC)
                    .typeName(Types.STRING_TYPE))
            .addElementInfo(it -> it.kind(ElementKind.FIELD)
                    .elementName("CONSTANT")
                    .addElementModifier(Modifier.FINAL)
                    .addElementModifier(Modifier.STATIC)
                    .accessModifier(AccessModifier.PRIVATE)
                    .typeName(Types.STRING_TYPE))
            .addElementInfo(it -> it.kind(ElementKind.METHOD)
                    .elementName("defaultMethod")
                    .addElementModifier(Modifier.DEFAULT)
                    .typeName(Types.STRING_TYPE))
            .addElementInfo(it -> it.kind(ElementKind.METHOD)
                    .elementName("staticMethodWithParams")
                    .addElementModifier(Modifier.STATIC)
                    .typeName(Types.STRING_TYPE)
                    .addParameterArgument(arg -> arg.typeName(Types.CONFIG_TYPE)
                            .elementName("config")
                            .kind(ElementKind.PARAMETER)))
            .addElementInfo(it -> it.kind(ElementKind.METHOD)
                    .elementName("staticMethodWithParams")
                    .addElementModifier(Modifier.STATIC)
                    .typeName(Types.STRING_TYPE)
                    .addAnnotation(annot -> annot.typeName(Types.PROTOTYPE_ANNOTATED_TYPE))
                    .addParameterArgument(arg -> arg.typeName(Types.CONFIG_TYPE)
                            .elementName("config")
                            .kind(ElementKind.PARAMETER))
                    .addParameterArgument(arg -> arg.typeName(Types.CONFIG_TYPE)
                            .elementName("config2")
                            .kind(ElementKind.PARAMETER)))
            .build();

    @Test
    void isMethodTest() {
        List<String> methods = TEST_SUBJECT.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .map(TypedElementInfo::elementName)
                .toList();

        assertThat(methods, containsInAnyOrder("defaultMethod",
                                               "staticMethodWithParams",
                                               "staticMethodWithParams"));
    }

    @Test
    void isStaticTest() {
        List<String> methods = TEST_SUBJECT.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isStatic)
                .map(TypedElementInfo::elementName)
                .toList();

        assertThat(methods, containsInAnyOrder("CONSTANT",
                                               "staticMethodWithParams",
                                               "staticMethodWithParams"));
    }

    @Test
    void isPrivateTest() {
        List<String> methods = TEST_SUBJECT.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isPrivate)
                .map(TypedElementInfo::elementName)
                .toList();

        assertThat(methods, containsInAnyOrder("privateField",
                                               "CONSTANT"));
    }

    @Test
    void isDefaultTest() {
        List<String> methods = TEST_SUBJECT.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isDefault)
                .map(TypedElementInfo::elementName)
                .toList();

        assertThat(methods, contains("defaultMethod"));
    }

    @Test
    void hasNoArgsTest() {
        List<String> methods = TEST_SUBJECT.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(ElementInfoPredicates::hasNoArgs)
                .map(TypedElementInfo::elementName)
                .toList();

        assertThat(methods, containsInAnyOrder("defaultMethod"));
    }

    @Test
    void hasAnnotationTest() {
        List<String> methods = TEST_SUBJECT.elementInfo()
                .stream()
                .filter(ElementInfoPredicates.hasAnnotation(Types.PROTOTYPE_ANNOTATED_TYPE))
                .map(TypedElementInfo::elementName)
                .toList();

        assertThat(methods, containsInAnyOrder("staticMethodWithParams"));
    }

    @Test
    void methodNameTest() {
        List<String> methods = TEST_SUBJECT.elementInfo()
                .stream()
                .filter(ElementInfoPredicates.elementName("staticMethodWithParams"))
                .map(TypedElementInfo::elementName)
                .toList();

        assertThat(methods, containsInAnyOrder("staticMethodWithParams",
                                               "staticMethodWithParams"));
    }

    @Test
    void hasParamsTest() {
        List<TypedElementInfo> methods = TEST_SUBJECT.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(ElementInfoPredicates.hasParams(Types.CONFIG_TYPE))
                .toList();

        assertThat(methods, hasSize(1));
        TypedElementInfo typedElementInfo = methods.get(0);
        assertThat(typedElementInfo.elementName(), is("staticMethodWithParams"));
        assertThat(typedElementInfo.parameterArguments(), hasSize(1));
    }

    @Test
    void ignoredMethodByNameTest() {
        List<String> methods = TEST_SUBJECT.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(BuilderInfoPredicates.ignoredMethod(Set.of(), Set.of("defaultMethod")))
                .map(TypedElementInfo::elementName)
                .toList();

        assertThat(methods, containsInAnyOrder("defaultMethod"));
    }

    @Test
    void ignoredMethodBySignatureTest() {
        List<TypedElementInfo> methods = TEST_SUBJECT.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(BuilderInfoPredicates.ignoredMethod(Set.of(new MethodSignature(Types.STRING_TYPE,
                                                                                       "staticMethodWithParams",
                                                                                       List.of(Types.CONFIG_TYPE,
                                                                                               Types.CONFIG_TYPE)
                )), Set.of()))
                .toList();

        assertThat(methods, hasSize(1));
        TypedElementInfo typedElementInfo = methods.get(0);
        assertThat(typedElementInfo.elementName(), is("staticMethodWithParams"));
        assertThat(typedElementInfo.parameterArguments(), hasSize(2));
    }

    @Test
    void findMethodTest() {
        Optional<TypedElementInfo> found = BuilderInfoPredicates.findMethod(new MethodSignature(Types.STRING_TYPE,
                                                                                                "staticMethodWithParams",
                                                                                                List.of(Types.CONFIG_TYPE,
                                                                                                        Types.CONFIG_TYPE)),
                                                                            null,
                                                                            TEST_SUBJECT);

        assertThat(found, not(Optional.empty()));

        TypedElementInfo foundMethod = found.get();
        assertThat(foundMethod.elementName(), is("staticMethodWithParams"));
        assertThat(foundMethod.parameterArguments(), hasSize(2));
    }
}