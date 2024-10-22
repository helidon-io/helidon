/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.codegen.classmodel;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.EnumValue;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

final class ContentSupport {
    private static final TypeName ANNOTATION = TypeName.create(Annotation.class);
    private static final TypeName ELEMENT = TypeName.create(TypedElementInfo.class);
    private static final TypeName ELEMENT_KIND = TypeName.create(ElementKind.class);
    private static final TypeName MODIFIER = TypeName.create(Modifier.class);
    private static final TypeName ACCESS_MODIFIER = TypeName.create(AccessModifier.class);

    private ContentSupport() {
    }

    static void addCreateElement(ContentBuilder<?> contentBuilder, TypedElementInfo element) {
        contentBuilder.addContent(ELEMENT)
                .addContentLine(".builder()")
                .increaseContentPadding()
                .increaseContentPadding();

        contentBuilder.addContent(".kind(")
                .addContent(ELEMENT_KIND)
                .addContent(".")
                .addContent(element.kind().name())
                .addContentLine(")");

        contentBuilder.addContent(".typeName(")
                .addContentCreate(element.typeName())
                .addContentLine(")");

        if (element.kind() != ElementKind.CONSTRUCTOR) {
            contentBuilder.addContent(".elementName(\"")
                    .addContent(element.elementName())
                    .addContentLine("\")");
        }

        for (Annotation annotation : element.annotations()) {
            contentBuilder.addContent(".addAnnotation(")
                    .addContentCreate(annotation)
                    .addContentLine(")");
        }

        for (Annotation annotation : element.inheritedAnnotations()) {
            contentBuilder.addContent(".addInheritedAnnotation(")
                    .addContentCreate(annotation)
                    .addContentLine(")");
        }

        AccessModifier accessModifier = element.accessModifier();
        if (accessModifier != AccessModifier.PACKAGE_PRIVATE) {
            contentBuilder.addContent(".accessModifier(")
                    .addContent(ACCESS_MODIFIER)
                    .addContent(".")
                    .addContent(accessModifier.name())
                    .addContentLine(")");

        }

        Set<Modifier> modifiers = element.elementModifiers();
        for (Modifier modifier : modifiers) {
            contentBuilder.addContent(".addElementModifier(")
                    .addContent(MODIFIER)
                    .addContent(".")
                    .addContent(modifier.name())
                    .addContentLine(")");
        }

        for (TypedElementInfo parameterArgument : element.parameterArguments()) {
            contentBuilder.addContent(".addParameterArgument(")
                    .addContentCreate(parameterArgument)
                    .addContentLine(")");
        }

        contentBuilder.addContentLine(".build()")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    static void addCreateAnnotation(ContentBuilder<?> contentBuilder, Annotation annotation) {

        Map<String, Object> values = annotation.values();
        if (values.isEmpty() && annotation.metaAnnotations().isEmpty()) {
            // Annotation.create(TypeName.create("my.type.AnnotationType"))
            contentBuilder.addContent(ANNOTATION)
                    .addContent(".create(")
                    .addContentCreate(annotation.typeName())
                    .addContent(")");
            return;
        }

        // Annotation.builder()
        //         .typeName(TypeName.create("my.type.AnnotationType"))
        contentBuilder.addContent(ANNOTATION)
                .addContentLine(".builder()")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".typeName(")
                .addContentCreate(annotation.typeName())
                .addContentLine(")");

        // .putValue("key", 14)
        annotation.values()
                .keySet()
                .forEach(propertyName -> {
                    contentBuilder.addContent(".putValue(\"")
                            .addContent(propertyName)
                            .addContent("\", ");
                    addAnnotationValue(contentBuilder, annotation.objectValue(propertyName).get());
                    contentBuilder.addContentLine(")");
                });

        // .addMetaAnnotation(...)
        annotation.metaAnnotations()
                        .forEach(it -> contentBuilder.addContent(".addMetaAnnotation(")
                                .increaseContentPadding()
                                .increaseContentPadding()
                                .addContentCreate(it)
                                .addContentLine(")")
                                .decreaseContentPadding()
                                .decreaseContentPadding());

        //  .build()
        contentBuilder.addContentLine(".build()")
                .decreaseContentPadding()
                .decreaseContentPadding();

    }

    static void addCreateTypeName(ContentBuilder<?> builder, TypeName typeName) {
        // TypeName.create("my.type.Name<my.type.TypeArgument>")
        builder.addContent(TypeNames.TYPE_NAME)
                .addContent(".create(\"")
                .addContent(typeName.resolvedName())
                .addContent("\")");
    }

    private static void addAnnotationValue(ContentBuilder<?> contentBuilder, Object objectValue) {
        switch (objectValue) {
        case String value -> contentBuilder.addContent("\"" + value + "\"");
        case Boolean value -> contentBuilder.addContent(String.valueOf(value));
        case Long value -> contentBuilder.addContent(String.valueOf(value) + 'L');
        case Double value -> contentBuilder.addContent(String.valueOf(value) + 'D');
        case Integer value -> contentBuilder.addContent(String.valueOf(value));
        case Byte value -> contentBuilder.addContent("(byte)" + value);
        case Character value -> contentBuilder.addContent("'" + value + "'");
        case Short value -> contentBuilder.addContent("(short)" + value);
        case Float value -> contentBuilder.addContent(String.valueOf(value) + 'F');
        case Class<?> value -> contentBuilder.addContentCreate(TypeName.create(value));
        case TypeName value -> contentBuilder.addContentCreate(value);
        case Annotation value -> contentBuilder.addContentCreate(value);
        case Enum<?> value -> toEnumValue(contentBuilder,
                                          EnumValue.create(TypeName.create(value.getDeclaringClass()), value.name()));
        case EnumValue value -> toEnumValue(contentBuilder, value);
        case List<?> values -> toListValues(contentBuilder, values);
        default -> throw new IllegalStateException("Unexpected annotation value type " + objectValue.getClass()
                .getName() + ": " + objectValue);
        }
    }

    private static void toListValues(ContentBuilder<?> contentBuilder, List<?> values) {
        contentBuilder.addContent(List.class)
                .addContent(".of(");
        int size = values.size();
        for (int i = 0; i < size; i++) {
            Object value = values.get(i);
            addAnnotationValue(contentBuilder, value);
            if (i != size - 1) {
                contentBuilder.addContent(",");
            }
        }
        contentBuilder.addContent(")");
    }

    private static void toEnumValue(ContentBuilder<?> contentBuilder, EnumValue enumValue) {
        // it would be easier to just use Enum.VALUE, but annotations and their dependencies
        // may not be on runtime classpath, so we have to work around it

        // EnumValue.create(TypeName.create(...), "VALUE")
        contentBuilder.addContent(EnumValue.class)
                .addContent(".create(")
                .addContentCreate(enumValue.type())
                .addContent(",")
                .addContent("\"")
                .addContent(enumValue.name())
                .addContent("\")");
    }
}
