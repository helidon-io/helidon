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

package io.helidon.codegen.test.codegen;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.List;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

class TestCodegenExtension implements CodegenExtension {
    private static final TypeName CRAZY = TypeName.create("io.helidon.codegen.test.codegen.use.CrazyAnnotation");
    private static final TypeName TARGET = TypeName.create(Target.class);

    private final CodegenContext ctx;

    TestCodegenExtension(CodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RoundContext roundContext) {
        Collection<TypeInfo> typeInfos = roundContext.annotatedTypes(TestCodegenExtensionProvider.WEIGHT);
        for (TypeInfo typeInfo : typeInfos) {
            process(typeInfo);
        }
    }

    private void process(TypeInfo typeInfo) {
        TypeName typeName = typeInfo.typeName();
        TypeName generatedType = TypeName.builder()
                .packageName(typeName.packageName())
                .className(typeName.className() + "__Generated")
                .build();

        var classModel = ClassModel.builder()
                .type(generatedType)
                .addAnnotation(annotation())
                .addField(f -> f
                        .isStatic(true)
                        .isFinal(true)
                        .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                        .name("ANNOTATION")
                        .type(Annotation.class)
                        .addContentCreate(annotation()))
                .addField(f -> f
                        .name("field")
                        .type(String.class)
                        .addAnnotation(annotation()))
                .addConstructor(ctr -> ctr.addAnnotation(annotation()))
                .addMethod(method -> method
                        .name("method")
                        .addAnnotation(annotation()));

        ctx.filer().writeSourceFile(classModel.build());
    }

    private Annotation annotation() {
        return Annotation.builder()
                .typeName(CRAZY)
                .putValue("stringValue", "value1")
                .putValue("booleanValue", true)
                .putValue("longValue", 49L)
                .putValue("doubleValue", 49.0D)
                .putValue("intValue", 49)
                .putValue("byteValue", (byte) 49)
                .putValue("charValue", 'x')
                .putValue("shortValue", (short) 49)
                .putValue("floatValue", 49.0F)
                .putValue("classValue", String.class)
                .putValue("typeValue", TypeName.create(String.class))
                .putValue("enumValue", ElementType.FIELD)
                .putValue("annotationValue", targetAnnotation(ElementType.CONSTRUCTOR))
                .putValue("lstring", List.of("value1", "value2"))
                .putValue("lboolean", List.of(true, false))
                .putValue("llong", List.of(49L, 50L))
                .putValue("ldouble", List.of(49.0, 50.0))
                .putValue("lint", List.of(49, 50))
                .putValue("lbyte", List.of((byte) 49, (byte) 50))
                .putValue("lchar", List.of('x', 'y'))
                .putValue("lshort", List.of((short) 49, (short) 50))
                .putValue("lfloat", List.of(49.0F, 50.0F))
                .putValue("lclass", List.of(String.class, Integer.class))
                .putValue("ltype",
                          List.of(TypeName.create(String.class), TypeName.create(Integer.class)))
                .putValue("lenum", List.of(ElementType.FIELD, ElementType.MODULE))
                .putValue("lannotation", List.of(targetAnnotation(ElementType.CONSTRUCTOR),
                                                 targetAnnotation(ElementType.FIELD)))
                .putValue("emptyList", List.of())
                .putValue("singletonList", List.of("value"))
                .build();
    }

    private Annotation targetAnnotation(ElementType elementType) {
        return Annotation.builder()
                .typeName(TARGET)
                .putValue("value", elementType)
                .build();
    }
}
