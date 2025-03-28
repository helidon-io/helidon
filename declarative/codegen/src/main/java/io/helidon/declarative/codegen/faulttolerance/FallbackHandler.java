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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;

import static io.helidon.declarative.codegen.DeclarativeTypes.THROWABLE;
import static io.helidon.declarative.codegen.faulttolerance.FtTypes.FALLBACK_ANNOTATION;
import static io.helidon.declarative.codegen.faulttolerance.FtTypes.FALLBACK_GENERATED_METHOD;

class FallbackHandler extends FtHandler {
    FallbackHandler(RegistryCodegenContext ctx) {
        super(ctx, FALLBACK_ANNOTATION);
    }

    @Override
    void process(RegistryRoundContext roundContext,
                 TypeInfo enclosingType,
                 TypedElementInfo element,
                 Annotation annotation,
                 TypeName generatedType,
                 ClassModel.Builder classModel) {
        TypeName enclosingTypeName = enclosingType.typeName();

        // class definition
        classModel.addInterface(fallbackMethodType(element.typeName(), enclosingTypeName));

        // generate the class body
        fallbackMethod(classModel, enclosingType, element, annotation);
        // parameterTypesMethod(classModel, element.parameterArguments());

        // add type to context
        addType(roundContext,
                generatedType,
                classModel,
                enclosingTypeName,
                element);
    }

    private void fallbackMethod(ClassModel.Builder classModel,
                                TypeInfo typeInfo,
                                TypedElementInfo element,
                                Annotation fallbackAnnotation) {

        addErrorChecker(classModel, fallbackAnnotation);

        String fallbackMethodName = fallbackAnnotation.value()
                .orElseThrow(() -> new CodegenException("value is mandatory on Fallback annotation. Missing for: " + element));
        TypeName returnType = element.typeName();
        boolean isVoid = TypeNames.PRIMITIVE_VOID.equals(returnType) || TypeNames.BOXED_VOID.equals(returnType);

        // then the implementation of interface method to fallback
        List<TypeName> expectedArguments = element.parameterArguments()
                .stream()
                .map(TypedElementInfo::typeName)
                .toList();
        List<TypeName> argsWithThrowable = new ArrayList<>(expectedArguments);
        argsWithThrowable.add(THROWABLE);

        List<TypedElementInfo> matchingByName = typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates.elementName(fallbackMethodName))
                .filter(ElementInfoPredicates::isMethod)
                .toList();
        if (matchingByName.isEmpty()) {
            throw new CodegenException("Could not find matching fallback method for  "
                                               + returnType.className() + " "
                                               + fallbackMethodName
                                               + "(" + expectedArguments.stream()
                    .map(TypeName::className)
                    .collect(Collectors.joining(", ")) + ")"
                                               + " in "
                                               + typeInfo.typeName().fqName() + ".",
                                       typeInfo.originatingElementValue());
        }

        // there is at least one method correctly named, let's find the right one
        boolean expectsThrowable = false;
        boolean found = false;
        TypedElementInfo fallbackMethod = null;
        List<BadCandidate> badCandidates = new ArrayList<>();

        Predicate<TypedElementInfo> withThrowable = ElementInfoPredicates.hasParams(argsWithThrowable);
        Predicate<TypedElementInfo> noThrowable = ElementInfoPredicates.hasParams(expectedArguments);
        for (TypedElementInfo elementInfo : matchingByName) {
            String reason;
            if (elementInfo.typeName().resolvedName().equals(returnType.resolvedName())) {
                if (withThrowable.test(elementInfo)) {
                    expectsThrowable = true;
                    found = true;
                    fallbackMethod = elementInfo;
                    break;
                }
                if (noThrowable.test(elementInfo)) {
                    found = true;
                    fallbackMethod = elementInfo;
                    break;
                }
                reason = "Method did not match arguments of annotated method";
            } else {
                reason = "Method return type did not match return type of annotated method";
            }

            badCandidates.add(new BadCandidate(elementInfo, reason));
        }
        if (!found) {
            throw new CodegenException("Could not find matching fallback method for  "
                                               + returnType.className() + " "
                                               + fallbackMethodName
                                               + "(" + expectedArguments.stream()
                    .map(TypeName::className)
                    .collect(Collectors.joining(", ")) + ")"
                                               + ", following bad candidates found: " + badCandidates);
        }
        boolean isStatic = fallbackMethod.elementModifiers().contains(Modifier.STATIC);

        boolean finalExpectsThrowable = expectsThrowable;
        // and the invocation of the fallback
        classModel.addMethod(fallback -> fallback
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(returnType.boxed())
                .name("fallback")
                .addParameter(serviceParam -> serviceParam
                        .type(typeInfo.typeName())
                        .name("service")
                )
                .addParameter(throwableParam -> throwableParam
                        .type(Throwable.class)
                        .name("throwable"))
                .addParameter(paramsParam -> paramsParam
                        .type(Object.class) // should be vararg
                        .vararg(true)
                        .name("params"))
                .addThrows(it -> it.type(Throwable.class))
                .addContentLine("if (ERROR_CHECKER.shouldSkip(throwable)) {")
                .addContentLine("throw throwable;")
                .decreaseContentPadding()
                .addContentLine("} else {")
                .update(it -> fallbackMethodBody(typeInfo,
                                                 element,
                                                 it,
                                                 fallbackMethodName,
                                                 isVoid,
                                                 isStatic,
                                                 finalExpectsThrowable))
                .addContentLine("}")
        );
    }

    private void fallbackMethodBody(TypeInfo typeInfo,
                                    TypedElementInfo element,
                                    Method.Builder method,
                                    String fallbackMethodName,
                                    boolean isVoid,
                                    boolean isStatic,
                                    boolean expectsThrowable) {
        if (!isVoid) {
            method.addContent("return ");
        }
        if (isStatic) {
            method.addContent(typeInfo.typeName());
        } else {
            method.addContent("service");
        }
        method.addContent(".")
                .addContent(fallbackMethodName)
                .addContent("(");

        // now all parameters (and maybe throwable)
        Iterator<TypedElementInfo> paramsIter = element.parameterArguments().iterator();
        int index = 0;
        while (paramsIter.hasNext()) {
            TypedElementInfo next = paramsIter.next();
            method.addContent("(")
                    .addContent(next.typeName())
                    .addContent(") params[")
                    .addContent(String.valueOf(index))
                    .addContent("]");
            if (paramsIter.hasNext()) {
                method.addContent(", ");
            }
            index++;
        }
        if (expectsThrowable) {
            if (!element.parameterArguments().isEmpty()) {
                method.addContent(", ");
            }
            method.addContent("throwable");
        }

        method.addContentLine(");");

        if (isVoid) {
            method.addContentLine("return null;");
        }
    }

    private TypeName fallbackMethodType(TypeName returnType, TypeName enclosingType) {
        return TypeName.builder(FALLBACK_GENERATED_METHOD)
                .addTypeArgument(returnType.boxed())
                .addTypeArgument(enclosingType)
                .build();
    }

    private record BadCandidate(TypedElementInfo element, String reason) {
    }

}
