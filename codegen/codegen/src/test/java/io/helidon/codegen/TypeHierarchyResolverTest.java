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
package io.helidon.codegen;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypeHierarchyResolverTest {

    /**
     * Verifies Java array return type covariance in the portable model.
     */
    @Test
    void implementsJavaArrayReturnCovariance() {
        TypeName bytes = TypeName.create(byte[].class);

        assertThat(effectiveReturnType(bytes, TypeNames.OBJECT), is(bytes));
        assertThat(effectiveReturnType(bytes, TypeName.create(Cloneable.class)), is(bytes));
        assertThat(effectiveReturnType(bytes, TypeName.create(Serializable.class)), is(bytes));
        assertThat(effectiveReturnType(TypeName.create(String[].class), TypeName.create(Object[].class)),
                   is(TypeName.create(String[].class)));
        assertThat(effectiveReturnType(TypeName.create(String[][].class), TypeName.create(Object[].class)),
                   is(TypeName.create(String[][].class)));
        assertThrows(CodegenException.class,
                     () -> effectiveReturnType(bytes, TypeName.create(int[].class)));
    }

    /**
     * Verifies that resolving a member type preserves structured type use annotations.
     */
    @Test
    void preservesTypeUseAnnotationsWhenResolvingMemberType() {
        Annotation annotation = Annotation.create(TypeName.create("example.ResultType"));
        TypeName variable = TypeName.builder()
                .className("T")
                .generic(true)
                .addAnnotation(annotation)
                .build();

        TypeName resolved = resolveStringMemberType(variable);

        assertThat(resolved, is(TypeNames.STRING));
        assertThat(resolved.annotations(), hasItem(annotation));
    }

    /**
     * Verifies that resolving a member type preserves annotations encoded in a generic declaration.
     */
    @Test
    void preservesEncodedTypeUseAnnotationsWhenResolvingMemberType() {
        TypeName variable = TypeName.createFromGenericDeclaration("@example.ResultType(\"mapped\") T");

        TypeName resolved = resolveStringMemberType(variable);

        Annotation annotation = resolved.findAnnotation(TypeName.create("example.ResultType")).orElseThrow();
        assertThat(annotation.stringValue().orElseThrow(), is("mapped"));
    }

    /**
     * Verifies that resolving a member type does not treat a concrete default package type as a type variable.
     */
    @Test
    void doesNotTreatAConcreteDefaultPackageMemberTypeAsATypeVariable() {
        TypeName concreteType = TypeName.builder()
                .className("T")
                .build();

        TypeName resolved = resolveStringMemberType(concreteType);

        assertThat(resolved, is(concreteType));
    }

    /**
     * Verifies a declaration member is resolved using the concrete arguments
     * of its enclosing type.
     */
    @Test
    void resolvesAParameterizedMemberType() {
        TypeName variable = TypeName.createFromGenericDeclaration("T");
        TypeName boxType = TypeName.create("example.Box");
        TypeName concreteBoxType = TypeName.builder(boxType)
                .addTypeArgument(TypeNames.STRING)
                .build();
        TypeInfo boxDeclaration = TypeInfo.builder()
                .typeName(TypeName.builder(boxType)
                                  .addTypeArgument(variable)
                                  .build())
                .rawType(boxType)
                .declaredType(TypeName.builder(boxType)
                                      .addTypeParameter("T")
                                      .addTypeArgument(variable)
                                      .build())
                .kind(ElementKind.RECORD)
                .build();
        TypeName optionalVariable = TypeName.builder(TypeNames.OPTIONAL)
                .addTypeArgument(variable)
                .build();

        TypeName resolved = TypeHierarchyResolver.create(_ -> Optional.empty())
                .resolveMemberType(boxDeclaration, concreteBoxType, optionalVariable);

        assertThat(resolved,
                   is(TypeName.builder(TypeNames.OPTIONAL)
                              .addTypeArgument(TypeNames.STRING)
                              .build()));
    }

    /**
     * Verifies implicit Java supertypes for round visible records, enums, and annotations.
     */
    @Test
    void recognizesImplicitSupertypesOfRoundVisibleTypes() {
        TypeName recordType = TypeName.create("example.RoundRecord");
        TypeName enumType = TypeName.create("example.RoundEnum");
        TypeName annotationType = TypeName.create("example.RoundAnnotation");
        Map<TypeName, TypeInfo> types = Map.of(recordType, typeInfo(recordType, ElementKind.RECORD),
                                               enumType, typeInfo(enumType, ElementKind.ENUM),
                                               annotationType,
                                               typeInfo(annotationType, ElementKind.ANNOTATION_TYPE));
        TypeHierarchyResolver resolver = TypeHierarchyResolver.create(type -> Optional.ofNullable(types.get(type)));

        assertThat(resolver.resolveSupertype(recordType, TypeName.create(Record.class)).isPresent(), is(true));
        assertThat(resolver.resolveSupertype(enumType, TypeName.create(Enum.class)).isPresent(), is(true));
        assertThat(resolver.resolveSupertype(annotationType,
                                             TypeName.create(java.lang.annotation.Annotation.class))
                           .isPresent(),
                   is(true));
    }

    /**
     * Verifies inherited interface variables are resolved in methods and declarations.
     */
    @Test
    void resolvesInheritedGenericMethods() {
        TypeName variable = TypeName.createFromGenericDeclaration("T");
        TypeName parentType = TypeName.create("example.GenericRepository");
        TypeInfo parent = TypeInfo.builder()
                .typeName(parentType)
                .declaredType(TypeName.builder(parentType)
                                      .addTypeParameter("T")
                                      .addTypeArgument(variable)
                                      .build())
                .kind(ElementKind.INTERFACE)
                .addElementInfo(method("find", variable, Modifier.ABSTRACT))
                .build();
        TypeName repositoryType = TypeName.create("example.StringRepository");
        TypeInfo repository = TypeInfo.builder()
                .typeName(repositoryType)
                .kind(ElementKind.INTERFACE)
                .addInterfaceTypeInfo(TypeInfo.builder(parent)
                                              .typeName(TypeName.builder(parentType)
                                                                .addTypeArgument(TypeNames.STRING)
                                                                .build())
                                              .build())
                .build();

        TypeHierarchyResolver.ResolvedMethod resolved = TypeHierarchyResolver.create(_ -> Optional.empty())
                .effectiveInterfaceMethods(repository)
                .getFirst();

        assertThat(resolved.method().typeName(), is(TypeNames.STRING));
        assertThat(resolved.declarations().getFirst().typeName(), is(TypeNames.STRING));
    }

    /**
     * Verifies a child default method satisfies an inherited abstract declaration.
     */
    @Test
    void retainsOnlyTheOverridingDefaultDeclaration() {
        TypeName parentType = TypeName.create("example.ParentRepository");
        TypeInfo parent = TypeInfo.builder()
                .typeName(parentType)
                .kind(ElementKind.INTERFACE)
                .addElementInfo(method("find", TypeNames.STRING, Modifier.ABSTRACT))
                .build();
        TypeInfo repository = TypeInfo.builder()
                .typeName(TypeName.create("example.DefaultRepository"))
                .kind(ElementKind.INTERFACE)
                .addElementInfo(method("find", TypeNames.STRING, Modifier.DEFAULT))
                .addInterfaceTypeInfo(parent)
                .build();

        TypeHierarchyResolver.ResolvedMethod resolved = TypeHierarchyResolver.create(_ -> Optional.empty())
                .effectiveInterfaceMethods(repository)
                .getFirst();

        assertThat(resolved.method().elementModifiers().contains(Modifier.DEFAULT), is(true));
        assertThat(resolved.declarations().size(), is(1));
    }

    /**
     * Verifies covariant selection retains unrelated source declarations for provider policy.
     */
    @Test
    void selectsACovariantMethodAndRetainsItsDeclarations() {
        TypeInfo objectParent = TypeInfo.builder()
                .typeName(TypeName.create("example.ObjectRepository"))
                .kind(ElementKind.INTERFACE)
                .addElementInfo(method("find", TypeNames.OBJECT, Modifier.ABSTRACT))
                .build();
        TypeInfo stringParent = TypeInfo.builder()
                .typeName(TypeName.create("example.StringRepository"))
                .kind(ElementKind.INTERFACE)
                .addElementInfo(method("find", TypeNames.STRING, Modifier.ABSTRACT))
                .build();
        TypeInfo repository = TypeInfo.builder()
                .typeName(TypeName.create("example.CovariantRepository"))
                .kind(ElementKind.INTERFACE)
                .addInterfaceTypeInfo(objectParent)
                .addInterfaceTypeInfo(stringParent)
                .build();

        TypeHierarchyResolver.ResolvedMethod resolved = TypeHierarchyResolver.create(_ -> Optional.empty())
                .effectiveInterfaceMethods(repository)
                .getFirst();

        assertThat(resolved.method().typeName(), is(TypeNames.STRING));
        assertThat(resolved.declarations().size(), is(2));
    }

    /**
     * Verifies a raw inherited interface erases bounded declaration variables.
     */
    @Test
    void erasesRawInheritedMethodTypes() {
        TypeName variable = TypeName.builder(TypeName.createFromGenericDeclaration("T"))
                .addUpperBound(TypeName.create(Number.class))
                .build();
        TypeName parentType = TypeName.create("example.NumberRepository");
        TypeInfo parent = TypeInfo.builder()
                .typeName(parentType)
                .declaredType(TypeName.builder(parentType)
                                      .addTypeParameter("T extends java.lang.Number")
                                      .addTypeArgument(variable)
                                      .build())
                .kind(ElementKind.INTERFACE)
                .addElementInfo(method("find", variable, Modifier.ABSTRACT))
                .build();
        TypeInfo repository = TypeInfo.builder()
                .typeName(TypeName.create("example.RawRepository"))
                .kind(ElementKind.INTERFACE)
                .addInterfaceTypeInfo(parent)
                .build();

        TypeHierarchyResolver.ResolvedMethod resolved = TypeHierarchyResolver.create(_ -> Optional.empty())
                .effectiveInterfaceMethods(repository)
                .getFirst();

        assertThat(resolved.method().typeName(), is(TypeName.create(Number.class)));
    }

    /**
     * Verifies the selected method declares only checked exceptions accepted by
     * every contributing declaration.
     */
    @Test
    void retainsCompatibleCheckedExceptions() {
        TypeName exceptionType = TypeName.create(Exception.class);
        TypeName ioExceptionType = TypeName.create(IOException.class);
        TypeInfo exceptionInfo = TypeInfo.builder()
                .typeName(exceptionType)
                .kind(ElementKind.CLASS)
                .build();
        TypeInfo ioExceptionInfo = TypeInfo.builder()
                .typeName(ioExceptionType)
                .kind(ElementKind.CLASS)
                .superTypeInfo(exceptionInfo)
                .build();
        TypedElementInfo broadMethod = TypedElementInfo.builder(method("find", TypeNames.STRING, Modifier.ABSTRACT))
                .throwsChecked(Set.of(exceptionType))
                .build();
        TypedElementInfo narrowMethod = TypedElementInfo.builder(method("find", TypeNames.STRING, Modifier.ABSTRACT))
                .throwsChecked(Set.of(ioExceptionType))
                .build();
        TypeInfo repository = repository(repository("example.BroadRepository", broadMethod),
                                         repository("example.NarrowRepository", narrowMethod));
        Map<TypeName, TypeInfo> types = Map.of(exceptionType, exceptionInfo,
                                               ioExceptionType, ioExceptionInfo);

        TypeHierarchyResolver.ResolvedMethod resolved = TypeHierarchyResolver.create(
                        type -> Optional.ofNullable(types.get(type)))
                .effectiveInterfaceMethods(repository)
                .getFirst();

        assertThat(resolved.method().throwsChecked(), is(Set.of(ioExceptionType)));
    }

    /**
     * Verifies incompatible inherited return types produce a useful diagnostic
     * that identifies both source declarations.
     */
    @Test
    void rejectsIncompatibleInheritedReturnTypes() {
        Object stringOrigin = new Object();
        Object integerOrigin = new Object();
        TypedElementInfo stringMethod = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName("find")
                .typeName(TypeNames.STRING)
                .addElementModifier(Modifier.ABSTRACT)
                .originatingElement(stringOrigin)
                .build();
        TypedElementInfo integerMethod = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName("find")
                .typeName(TypeName.create(Integer.class))
                .addElementModifier(Modifier.ABSTRACT)
                .originatingElement(integerOrigin)
                .build();
        TypeInfo repository = repository(repository("example.StringRepository", stringMethod),
                                         repository("example.IntegerRepository", integerMethod));

        CodegenException failure = assertThrows(CodegenException.class,
                                                () -> TypeHierarchyResolver.create(_ -> Optional.empty())
                                                        .effectiveInterfaceMethods(repository));

        assertThat(failure.getMessage(),
                   is("Inherited method 'find()' has return types that cannot be implemented by one method."));
        assertThat(failure.originatingElements(), is(List.of(stringOrigin, integerOrigin)));
    }

    /**
     * Resolves a member type against an enclosing type whose variable is bound to {@link String}.
     *
     * @param memberType member type to resolve
     * @return resolved member type
     */
    private static TypeName resolveStringMemberType(TypeName memberType) {
        TypeName variable = TypeName.createFromGenericDeclaration("T");
        TypeName declarationType = TypeName.create("example.GenericType");
        TypeInfo declaration = TypeInfo.builder()
                .typeName(TypeName.builder(declarationType)
                                  .addTypeArgument(variable)
                                  .build())
                .rawType(declarationType)
                .declaredType(TypeName.builder(declarationType)
                                      .addTypeParameter("T")
                                      .addTypeArgument(variable)
                                      .build())
                .kind(ElementKind.CLASS)
                .build();
        TypeName useSiteType = TypeName.builder(declarationType)
                .addTypeArgument(TypeNames.STRING)
                .build();
        return TypeHierarchyResolver.create(_ -> Optional.empty())
                .resolveMemberType(declaration, useSiteType, memberType);
    }

    private static TypeName effectiveReturnType(TypeName first, TypeName second) {
        TypeInfo repository = repository(repository("example.FirstRepository",
                                                    method("find", first, Modifier.ABSTRACT)),
                                         repository("example.SecondRepository",
                                                    method("find", second, Modifier.ABSTRACT)));
        return TypeHierarchyResolver.create(_ -> Optional.empty())
                .effectiveInterfaceMethods(repository)
                .getFirst()
                .method()
                .typeName();
    }

    private static TypeInfo typeInfo(TypeName typeName, ElementKind kind) {
        return TypeInfo.builder()
                .typeName(typeName)
                .kind(kind)
                .build();
    }

    private static TypedElementInfo method(String name, TypeName returnType, Modifier modifier) {
        return TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName(name)
                .typeName(returnType)
                .addElementModifier(modifier)
                .build();
    }

    private static TypeInfo repository(TypeInfo... parents) {
        return TypeInfo.builder()
                .typeName(TypeName.create("example.CheckedRepository"))
                .kind(ElementKind.INTERFACE)
                .interfaceTypeInfo(List.of(parents))
                .build();
    }

    private static TypeInfo repository(String name, TypedElementInfo method) {
        return TypeInfo.builder()
                .typeName(TypeName.create(name))
                .kind(ElementKind.INTERFACE)
                .addElementInfo(method)
                .build();
    }
}
