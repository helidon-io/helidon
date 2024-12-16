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

package io.helidon.codegen;

import java.util.List;
import java.util.stream.Collectors;

import io.helidon.codegen.classmodel.Annotation;
import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Executable;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Transforms class model to a {@link io.helidon.common.types.TypeInfo}.
 */
final class ClassModelFactory {
    private ClassModelFactory() {
    }

    static TypeInfo create(RoundContext ctx,
                           TypeName requestedTypeName,
                           ClassBase requestedType) {

        var builder = TypeInfo.builder()
                .typeName(requestedTypeName)
                .kind(requestedType.kind())
                .accessModifier(requestedType.accessModifier())
                .description(String.join("\n", requestedType.description()));

        for (Annotation annotation : requestedType.annotations()) {
            builder.addAnnotation(annotation.toTypesAnnotation());
        }

        requestedType.superTypeName()
                .flatMap(ctx::typeInfo)
                .ifPresent(builder::superTypeInfo);

        List<TypeName> typeNames = requestedType.interfaceTypeNames();
        for (TypeName typeName : typeNames) {
            ctx.typeInfo(typeName).ifPresent(builder::addInterfaceTypeInfo);
        }
        for (Field field : requestedType.fields()) {
            addField(builder, field);
        }
        for (Constructor constructor : requestedType.constructors()) {
            addConstructor(requestedTypeName, builder, constructor);
        }
        for (Method method : requestedType.methods()) {
            addMethod(builder, method);
        }

        for (ClassBase innerClass : requestedType.innerClasses()) {
            addInnerClass(requestedTypeName, builder, innerClass);
        }

        return builder.build();
    }

    private static void addInnerClass(TypeName requestedTypeName, TypeInfo.Builder builder, ClassBase innerClass) {

        builder.addElementInfo(innerInfo -> innerInfo
                .typeName(innerClassTypeName(requestedTypeName, innerClass.name()))
                .kind(ElementKind.CLASS)
                .elementName(innerClass.name())
                .accessModifier(innerClass.accessModifier())
                .update(it -> {
                    if (innerClass.isStatic()) {
                        it.addElementModifier(Modifier.STATIC);
                    }
                    if (innerClass.isAbstract()) {
                        it.addElementModifier(Modifier.ABSTRACT);
                    }
                    if (innerClass.isFinal()) {
                        it.addElementModifier(Modifier.FINAL);
                    }
                })
                .description(String.join("\n", innerClass.description()))
                .update(it -> addAnnotations(it, innerClass.annotations()))
        );
    }

    private static TypeName innerClassTypeName(TypeName requestedTypeName, String name) {
        return TypeName.builder(requestedTypeName)
                .addEnclosingName(requestedTypeName.className())
                .className(name)
                .build();
    }

    private static void addMethod(TypeInfo.Builder builder, Method method) {
        builder.addElementInfo(methodInfo -> methodInfo
                .kind(ElementKind.METHOD)
                .elementName(method.name())
                .accessModifier(method.accessModifier())
                .update(it -> {
                    if (method.isStatic()) {
                        it.addElementModifier(Modifier.STATIC);
                    }
                    if (method.isFinal()) {
                        it.addElementModifier(Modifier.FINAL);
                    }
                    if (method.isAbstract()) {
                        it.addElementModifier(Modifier.ABSTRACT);
                    }
                    if (method.isDefault()) {
                        it.addElementModifier(Modifier.DEFAULT);
                    }
                })
                .description(String.join("\n", method.description()))
                .update(it -> addAnnotations(it, method.annotations()))
                .update(it -> processExecutable(it, method))
                .typeName(method.typeName())
        );
    }

    private static void processExecutable(TypedElementInfo.Builder builder, Executable executable) {
        for (Parameter parameter : executable.parameters()) {
            builder.addParameterArgument(arg -> arg
                    .kind(ElementKind.PARAMETER)
                    .elementName(parameter.name())
                    .typeName(parameter.typeName())
                    .description(String.join("\n", parameter.description()))
                    .update(it -> addAnnotations(it, parameter.annotations()))
            );
        }
        builder.addThrowsChecked(executable.exceptions()
                                         .stream()
                                         .collect(Collectors.toUnmodifiableSet()));
    }

    private static void addConstructor(TypeName typeName, TypeInfo.Builder builder, Constructor constructor) {
        builder.addElementInfo(ctrInfo -> ctrInfo
                .typeName(typeName)
                .kind(ElementKind.CONSTRUCTOR)
                .accessModifier(constructor.accessModifier())
                .description(String.join("\n", constructor.description()))
                .update(it -> addAnnotations(it, constructor.annotations()))
                .update(it -> processExecutable(it, constructor))
        );
    }

    private static void addField(TypeInfo.Builder builder, Field field) {
        builder.addElementInfo(fieldInfo -> fieldInfo
                .typeName(field.typeName())
                .kind(ElementKind.FIELD)
                .accessModifier(field.accessModifier())
                .elementName(field.name())
                .description(String.join("\n", field.description()))
                .update(it -> addAnnotations(it, field.annotations()))
                .update(it -> {
                    if (field.isStatic()) {
                        it.addElementModifier(Modifier.STATIC);
                    }
                    if (field.isFinal()) {
                        it.addElementModifier(Modifier.FINAL);
                    }
                    if (field.isVolatile()) {
                        it.addElementModifier(Modifier.VOLATILE);
                    }
                })
        );
    }

    private static void addAnnotations(TypedElementInfo.Builder element, List<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            element.addAnnotation(annotation.toTypesAnnotation());
        }
    }
}
