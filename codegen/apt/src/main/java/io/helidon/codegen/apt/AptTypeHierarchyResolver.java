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
package io.helidon.codegen.apt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import io.helidon.codegen.TypeHierarchy;
import io.helidon.codegen.TypeHierarchyResolver;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Adapts {@link TypeHierarchyResolver} to the annotation processing type system.
 * <p>
 * Uses compiler elements and types to resolve inherited generic method signatures, apply Java override and return type
 * rules, and retain the actual type arguments of a requested supertype. When the compiler model cannot represent a
 * query, the resolver returns an empty result so the base class can use its portable code generation type model.
 */
final class AptTypeHierarchyResolver extends TypeHierarchyResolver {

    private final Elements elements;
    private final Types types;
    private final TypeMirror exceptionType;
    private final TypeMirror runtimeExceptionType;

    // Retain the compiler utilities and exception types used by every hierarchy query for this processing environment.
    AptTypeHierarchyResolver(ProcessingEnvironment environment,
                             Function<TypeName, Optional<TypeInfo>> typeInfoLookup) {
        super(typeInfoLookup);
        ProcessingEnvironment resolvedEnvironment = Objects.requireNonNull(environment,
                                                                           "The processing environment must not be null.");
        this.elements = resolvedEnvironment.getElementUtils();
        this.types = resolvedEnvironment.getTypeUtils();
        this.exceptionType = elements.getTypeElement(Exception.class.getName()).asType();
        this.runtimeExceptionType = elements.getTypeElement(RuntimeException.class.getName()).asType();
    }

    // Resolve an inherited declaration in the root interface's generic context, then copy the compiler-resolved
    // signature back into the portable code generation model while retaining source annotations and vararg metadata.
    @Override
    protected Optional<TypedElementInfo> resolveEnvironmentMember(TypeInfo interfaceInfo,
                                                                  TypedElementInfo declaration) {
        Optional<ExecutableType> memberType = memberType(interfaceInfo, declaration);
        if (memberType.isEmpty()) {
            return Optional.empty();
        }

        ExecutableType executableType = memberType.get();
        List<? extends TypeMirror> parameterTypes = executableType.getParameterTypes();
        List<TypedElementInfo> sourceParameters = declaration.parameterArguments();
        if (parameterTypes.size() != sourceParameters.size()) {
            return Optional.empty();
        }

        Optional<TypeName> returnType = AptTypeFactory.createTypeName(executableType.getReturnType());
        if (returnType.isEmpty()) {
            return Optional.empty();
        }
        List<TypedElementInfo> parameters = new ArrayList<>(sourceParameters.size());
        for (int index = 0; index < sourceParameters.size(); index++) {
            TypedElementInfo sourceParameter = sourceParameters.get(index);
            Optional<TypeName> parameterType = AptTypeFactory.createTypeName(parameterTypes.get(index));
            if (parameterType.isEmpty()) {
                return Optional.empty();
            }
            TypeName resolvedType = TypeHierarchy.mergeTypeNameAnnotations(parameterType.get(),
                                                                            sourceParameter.typeName());
            if (sourceParameter.typeName().vararg()) {
                resolvedType = TypeName.builder(resolvedType)
                        .vararg(true)
                        .build();
            }
            parameters.add(TypedElementInfo.builder(sourceParameter)
                                   .typeName(resolvedType)
                                   .enclosingType(interfaceInfo.typeName().genericTypeName())
                                   .build());
        }

        Set<TypeName> checkedExceptions = new LinkedHashSet<>();
        for (TypeMirror thrownType : executableType.getThrownTypes()) {
            if (types.isAssignable(thrownType, exceptionType)
                    && !types.isAssignable(thrownType, runtimeExceptionType)) {
                AptTypeFactory.createTypeName(thrownType).ifPresent(checkedExceptions::add);
            }
        }
        List<TypeName> typeParameters = executableType.getTypeVariables()
                .stream()
                .map(AptTypeFactory::createTypeName)
                .flatMap(Optional::stream)
                .toList();
        return Optional.of(TypedElementInfo.builder(declaration)
                                   .typeName(TypeHierarchy.mergeTypeNameAnnotations(returnType.get(),
                                                                                    declaration.typeName()))
                                   .parameterArguments(parameters)
                                   .throwsChecked(checkedExceptions)
                                   .typeParameters(typeParameters)
                                   .enclosingType(interfaceInfo.typeName().genericTypeName())
                                   .build());
    }

    // Ask the compiler to apply the Java override rules when all three model objects retain their originating elements.
    @Override
    protected Optional<Boolean> environmentOverrides(TypeInfo interfaceInfo,
                                                     TypedElementInfo overrider,
                                                     TypedElementInfo overridden) {
        if (interfaceInfo.originatingElementValue() instanceof TypeElement typeElement
                && overrider.originatingElementValue() instanceof ExecutableElement overridingElement
                && overridden.originatingElementValue() instanceof ExecutableElement overriddenElement) {
            return Optional.of(elements.overrides(overridingElement, overriddenElement, typeElement));
        }
        return Optional.empty();
    }

    // Compare return types after the compiler has substituted the root interface's type arguments. Primitive and void
    // returns require an exact match, while unresolved method type variables are left to the portable resolver.
    @Override
    protected Optional<Boolean> environmentReturnTypeAssignable(TypeInfo interfaceInfo,
                                                                TypedElementInfo candidate,
                                                                TypedElementInfo existing) {
        Optional<ExecutableType> candidateType = memberType(interfaceInfo, candidate);
        Optional<ExecutableType> existingType = memberType(interfaceInfo, existing);
        if (candidateType.isEmpty() || existingType.isEmpty()) {
            return Optional.empty();
        }
        TypeMirror candidateReturn = candidateType.get().getReturnType();
        TypeMirror existingReturn = existingType.get().getReturnType();
        if (types.isSameType(candidateReturn, existingReturn)) {
            return Optional.of(true);
        }
        if (candidateReturn.getKind().isPrimitive()
                || existingReturn.getKind().isPrimitive()
                || candidateReturn.getKind() == TypeKind.VOID
                || existingReturn.getKind() == TypeKind.VOID) {
            return Optional.of(false);
        }
        if (types.isAssignable(candidateReturn, existingReturn)) {
            return Optional.of(true);
        }
        if (!candidate.typeParameters().isEmpty() || !existing.typeParameters().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(false);
    }

    // Walk the type hierarchy, matching the requested declaration by erasure but returning the actual
    // parameterized supertype encountered in the candidate's hierarchy.
    @Override
    protected Optional<TypeName> resolveEnvironmentSupertype(TypeName candidate, TypeName expected) {
        Optional<TypeMirror> candidateMirror = typeMirror(candidate);
        TypeElement expectedElement = elements.getTypeElement(expected.genericTypeName().fqName());
        if (candidateMirror.isEmpty() || expectedElement == null) {
            return Optional.empty();
        }

        TypeMirror expectedErasure = types.erasure(expectedElement.asType());
        Queue<TypeMirror> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(candidateMirror.get());
        while (!pending.isEmpty()) {
            TypeMirror current = pending.remove();
            if (!visited.add(current.toString())) {
                continue;
            }
            if (types.isSameType(types.erasure(current), expectedErasure)) {
                return AptTypeFactory.createTypeName(current);
            }
            pending.addAll(types.directSupertypes(current));
        }
        return Optional.empty();
    }

    // Project a source declaration into the inspected interface so inherited type arguments are substituted by the
    // compiler.
    private Optional<ExecutableType> memberType(TypeInfo interfaceInfo, TypedElementInfo declaration) {
        if (!(interfaceInfo.originatingElementValue() instanceof TypeElement typeElement)
                || !(typeElement.asType() instanceof DeclaredType declaredType)
                || !(declaration.originatingElementValue() instanceof ExecutableElement executableElement)) {
            return Optional.empty();
        }
        TypeMirror memberType = types.asMemberOf(declaredType, executableElement);
        return memberType instanceof ExecutableType executableType
                ? Optional.of(executableType)
                : Optional.empty();
    }

    // Recreate compiler mirrors recursively for concrete model types. Type variables and wildcards need a declaration
    // context that is not available here, so those shapes deliberately fall back to the portable type model.
    private Optional<TypeMirror> typeMirror(TypeName typeName) {
        if (typeName.array()) {
            return typeName.componentType()
                    .flatMap(this::typeMirror)
                    .map(types::getArrayType);
        }
        if (typeName.primitive()) {
            return primitiveType(typeName);
        }
        if (typeName.generic() || typeName.wildcard()) {
            return Optional.empty();
        }
        TypeElement typeElement = elements.getTypeElement(typeName.genericTypeName().fqName());
        if (typeElement == null) {
            return Optional.empty();
        }
        List<TypeMirror> arguments = new ArrayList<>(typeName.typeArguments().size());
        for (TypeName typeArgument : typeName.typeArguments()) {
            Optional<TypeMirror> argument = typeMirror(typeArgument);
            if (argument.isEmpty()) {
                return Optional.empty();
            }
            arguments.add(argument.get());
        }
        return Optional.of(types.getDeclaredType(typeElement, arguments.toArray(TypeMirror[]::new)));
    }

    // Convert the portable primitive name to the corresponding compiler type kind.
    private Optional<TypeMirror> primitiveType(TypeName typeName) {
        return switch (typeName.name()) {
            case "boolean" -> Optional.of(types.getPrimitiveType(TypeKind.BOOLEAN));
            case "byte" -> Optional.of(types.getPrimitiveType(TypeKind.BYTE));
            case "char" -> Optional.of(types.getPrimitiveType(TypeKind.CHAR));
            case "double" -> Optional.of(types.getPrimitiveType(TypeKind.DOUBLE));
            case "float" -> Optional.of(types.getPrimitiveType(TypeKind.FLOAT));
            case "int" -> Optional.of(types.getPrimitiveType(TypeKind.INT));
            case "long" -> Optional.of(types.getPrimitiveType(TypeKind.LONG));
            case "short" -> Optional.of(types.getPrimitiveType(TypeKind.SHORT));
            default -> Optional.empty();
        };
    }
}
