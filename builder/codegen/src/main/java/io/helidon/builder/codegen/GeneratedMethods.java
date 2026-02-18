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

package io.helidon.builder.codegen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

class GeneratedMethods {
    private GeneratedMethods() {
    }

    public static GeneratedMethod createPrototypeMethod(TypeName declaringType,
                                                        TypedElementInfo referencedMethod,
                                                        List<Annotation> annotations,
                                                        TypedElementInfo defaultMethodOnBlueprint) {
        return prototypeMethodBuilder(declaringType,
                                      referencedMethod,
                                      annotations,
                                      referencedMethod.description()
                                              .filter(Predicate.not(String::isBlank))
                                              .or(defaultMethodOnBlueprint::description)
                                              .orElse(null));
    }

    static GeneratedMethod createPrototypeMethod(TypeName declaringType,
                                                 TypedElementInfo referenceMethod,
                                                 List<Annotation> annotations) {

        return prototypeMethodBuilder(declaringType, referenceMethod, annotations, referenceMethod.description().orElse(null));
    }

    static GeneratedMethod createFactoryMethod(TypeName declaringType,
                                               TypedElementInfo referenceMethod,
                                               List<Annotation> annotations) {
        TypedElementInfo newOne = TypedElementInfo.builder()
                .from(referenceMethod)
                .clearEnclosingType()
                .accessModifier(AccessModifier.PUBLIC)
                .annotations(annotations)
                .elementModifiers(Set.of(Modifier.STATIC))
                .build();

        List<String> paramNames = referenceMethod.parameterArguments()
                .stream()
                .map(TypedElementInfo::elementName)
                .toList();

        Consumer<ContentBuilder<?>> content = cb -> {
            if (!newOne.typeName().equals(TypeNames.PRIMITIVE_VOID)) {
                cb.addContent("return ");
            }
            cb.addContent(declaringType)
                    .addContent(".")
                    .addContent(referenceMethod.elementName())
                    .addContent("(");

            cb.addContent(String.join(", ", paramNames));

            cb.addContentLine(");");
        };

        return GeneratedMethod.builder()
                .method(newOne)
                .contentBuilder(content)
                .update(it -> referenceMethod.description().map(Javadoc::parse).ifPresent(it::javadoc))
                .build();
    }

    static GeneratedMethod createBuilderMethod(TypeName declaringType,
                                               TypedElementInfo referenceMethod,
                                               List<Annotation> annotations) {

        var list = referenceMethod.parameterArguments();
        if (list.isEmpty()) {
            throw new CodegenException("Custom builder method must have at least one parameter - the builder base itself",
                                       referenceMethod);
        }

        var newList = new ArrayList<>(list);
        // remove the first parameter
        newList.removeFirst();

        List<String> paramNames = newList.stream()
                .map(TypedElementInfo::elementName)
                .toList();

        Javadoc javadoc = Javadoc.parse(referenceMethod.description().orElse(""));
        var originalParamsJavadoc = javadoc.parameters();
        Map<String, List<String>> newParamsJavadoc = new LinkedHashMap<>();
        for (String paramName : paramNames) {
            var doc = originalParamsJavadoc.get(paramName);
            if (doc != null) {
                newParamsJavadoc.put(paramName, doc);
            }
        }
        Javadoc newJavadoc = Javadoc.builder()
                .from(javadoc)
                .parameters(newParamsJavadoc)
                .returnDescription("updated builder instance")
                .build();

        TypedElementInfo newOne = TypedElementInfo.builder()
                .from(referenceMethod)
                .typeName(Utils.builderReturnType())
                .clearEnclosingType()
                .parameterArguments(newList)
                .accessModifier(AccessModifier.PUBLIC)
                .annotations(annotations)
                .clearModifiers()
                .elementModifiers(Set.of())
                .build();

        Consumer<ContentBuilder<?>> content = cb -> {
            cb.addContent(declaringType)
                    .addContent(".")
                    .addContent(referenceMethod.elementName())
                    .addContent("(this");

            if (!paramNames.isEmpty()) {
                cb.addContent(", ");
            }

            cb.addContent(String.join(", ", paramNames));

            cb.addContentLine(");")
                    .addContentLine("return self();");

        };

        return GeneratedMethod.builder()
                .javadoc(newJavadoc)
                .method(newOne)
                .contentBuilder(content)
                .build();

    }

    // description may be null
    private static GeneratedMethod prototypeMethodBuilder(TypeName declaringType,
                                                          TypedElementInfo referenceMethod,
                                                          List<Annotation> annotations,
                                                          String description) {
        var list = referenceMethod.parameterArguments();
        if (list.isEmpty()) {
            throw new CodegenException("Custom prototype method must have at least one parameter - the prototype itself",
                                       referenceMethod);
        }

        var newList = new ArrayList<>(list);
        // remove the first parameter
        newList.removeFirst();

        List<String> paramNames = newList.stream()
                .map(TypedElementInfo::elementName)
                .toList();

        TypedElementInfo newOne = TypedElementInfo.builder()
                .from(referenceMethod)
                .clearEnclosingType()
                .parameterArguments(newList)
                .accessModifier(AccessModifier.PUBLIC)
                .annotations(annotations)
                .clearModifiers()
                .elementModifiers(Set.of(Modifier.DEFAULT))
                .build();

        Consumer<ContentBuilder<?>> content = cb -> {
            if (!newOne.typeName().equals(TypeNames.PRIMITIVE_VOID)) {
                cb.addContent("return ");
            }
            cb.addContent(declaringType)
                    .addContent(".")
                    .addContent(referenceMethod.elementName())
                    .addContent("(this");

            if (!paramNames.isEmpty()) {
                cb.addContent(", ");
            }

            cb.addContent(String.join(", ", paramNames));

            cb.addContentLine(");");
        };

        var gmb = GeneratedMethod.builder()
                .method(newOne)
                .contentBuilder(content);

        if (description != null && !description.isBlank()) {
            Javadoc javadoc = Javadoc.parse(description);
            var originalParamsJavadoc = javadoc.parameters();
            Map<String, List<String>> newParamsJavadoc = new LinkedHashMap<>();
            for (String paramName : paramNames) {
                var doc = originalParamsJavadoc.get(paramName);
                if (doc != null) {
                    newParamsJavadoc.put(paramName, doc);
                }
            }
            Javadoc newJavadoc = Javadoc.builder()
                    .from(javadoc)
                    .parameters(newParamsJavadoc)
                    .build();
            gmb.javadoc(newJavadoc);
        }

        return gmb.build();
    }
}
