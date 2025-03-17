/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.faulttolerance;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Field;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;

import static io.helidon.declarative.codegen.DeclarativeTypes.SET_OF_THROWABLES;
import static io.helidon.declarative.codegen.DeclarativeTypes.SINGLETON_ANNOTATION;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED;

abstract class FtHandler {
    private final RegistryCodegenContext ctx;
    private final TypeName annotation;
    private final TypeName generator;

    FtHandler(RegistryCodegenContext ctx,
              TypeName annotation) {
        this.ctx = ctx;
        this.annotation = annotation;
        this.generator = TypeName.create(getClass());
    }

    void process(RegistryRoundContext roundCtx, Map<TypeName, TypeInfo> types) {
        var elements = roundCtx.annotatedElements(annotation);
        int index = 0;
        for (TypedElementInfo element : elements) {
            var enclosingType = enclosingType(types, element);
            var generatedType = generatedTypeName(enclosingType.typeName(), element, index);
            process(roundCtx,
                    enclosingType,
                    element,
                    element.annotation(annotation),
                    generatedType,
                    classBuilder(enclosingType.typeName(),
                                 element,
                                 generatedType));
            index++;
        }
    }

    TypeInfo enclosingType(Map<TypeName, TypeInfo> types, TypedElementInfo element) {
        if (element.enclosingType().isEmpty()) {
            throw new CodegenException(
                    annotation.className()
                            + " annotation is only allowed on a method, yet this element does not have an enclosing type: "
                            + element.elementName(),
                    element.originatingElementValue());
        }

        TypeName enclosingType = element.enclosingType().get();
        TypeInfo typeInfo = types.get(enclosingType);
        if (typeInfo == null) {
            throw new CodegenException(
                    annotation.className() + " annotation is expected on a type processed as part of this processing round, "
                            + "yet the type info is not available for type: " + enclosingType.fqName(),
                    element.originatingElementValue());
        }
        return typeInfo;
    }

    abstract void process(RegistryRoundContext roundContext,
                          TypeInfo enclosingType,
                          TypedElementInfo element,
                          Annotation ftAnnotation,
                          TypeName generatedType,
                          ClassModel.Builder classModel);

    Annotation namedAnnotation(TypeName enclosingTypeName,
                               TypedElementInfo element) {
        return namedAnnotation(enclosingTypeName.fqName() + "." + element.signature().text());
    }

    Annotation namedAnnotation(String name) {
        return Annotation.create(SERVICE_ANNOTATION_NAMED, name);
    }

    void addType(RegistryRoundContext roundCtx,
                 TypeName generatedType,
                 ClassModel.Builder classModel,
                 TypeName enclosingTypeName,
                 TypedElementInfo element) {
        roundCtx.addGeneratedType(generatedType,
                                  classModel,
                                  enclosingTypeName,
                                  element.originatingElementValue());
    }

    void addErrorChecker(ClassModel.Builder classModel, Annotation annotation) {
        // we need set of throwables to apply on, skip on, and error check fields
        List<TypeName> applyOn = annotation.typeValues("applyOn")
                .orElseGet(List::of);
        List<TypeName> skipOn = annotation.typeValues("skipOn")
                .orElseGet(List::of);

        classModel.addField(applyOnField -> applyOnField
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .isStatic(true)
                .type(SET_OF_THROWABLES)
                .name("APPLY_ON")
                .update(it -> throwableSet(it, applyOn))
        );

        classModel.addField(skipOnField -> skipOnField
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .isStatic(true)
                .type(SET_OF_THROWABLES)
                .name("SKIP_ON")
                .update(it -> throwableSet(it, skipOn))
        );

        classModel.addField(errorChecker -> errorChecker
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .isStatic(true)
                .type(FtTypes.ERROR_CHECKER)
                .name("ERROR_CHECKER")
                .addContent(FtTypes.ERROR_CHECKER)
                .addContent(".create(SKIP_ON, APPLY_ON)")
        );
    }

    private void throwableSet(Field.Builder field, List<TypeName> listOfThrowables) {
        field.addContent(Set.class)
                .addContent(".of(");
        if (listOfThrowables.isEmpty()) {
            field.addContent(")");
            return;
        }
        field.addContentLine("");
        field.increaseContentPadding()
                .increaseContentPadding()
                .update(it -> {
                    Iterator<TypeName> iterator = listOfThrowables.iterator();
                    while (iterator.hasNext()) {
                        it.addContent(iterator.next())
                                .addContent(".class");
                        if (iterator.hasNext()) {
                            it.addContent(",");
                        }
                        it.addContentLine("");
                    }
                })
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContent(")");
    }

    private ClassModel.Builder classBuilder(TypeName enclosingType,
                                            TypedElementInfo element,
                                            TypeName generatedType) {
        return ClassModel.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(SINGLETON_ANNOTATION)
                .addAnnotation(namedAnnotation(enclosingType, element))
                .addAnnotation(CodegenUtil.generatedAnnotation(generator, enclosingType, generatedType, "1", ""))
                .copyright(CodegenUtil.copyright(generator, enclosingType, generatedType))
                .type(generatedType)
                .sortStaticFields(false);
    }

    private TypeName generatedTypeName(TypeName typeName, TypedElementInfo element, int index) {
        return TypeName.builder()
                .packageName(typeName.packageName())
                .className(typeName.classNameWithEnclosingNames().replace('.', '_') + "_"
                                   + element.elementName() + (index == 0 ? "" : "_" + index)
                                   + "__" + annotation.className())
                .build();
    }
}
