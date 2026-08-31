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
package io.helidon.data.jdbc.codegen;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcTypeHierarchyTest {

    /**
     * Verifies unrelated inherited methods cannot declare different JDBC policy.
     */
    @Test
    void rejectsConflictingJdbcAnnotations() {
        TypeInfo left = repository("example.LeftRepository",
                                   method("find", TypeNames.STRING, "select LEFT_VALUE from ITEM"));
        TypeInfo right = repository("example.RightRepository",
                                    method("find", TypeNames.STRING, "select RIGHT_VALUE from ITEM"));
        TypeInfo repository = repository("example.Repository", left, right);

        CodegenException failure = assertThrows(CodegenException.class,
                                                () -> JdbcTypeHierarchy.abstractMethods(repository,
                                                                                       new TypesRoundContext(Map.of())));

        assertThat(failure.getMessage(),
                   is("Inherited repository method 'find()' has conflicting JDBC or transaction annotations."));
    }

    /**
     * Verifies named SQL markers keep the same physical parameter meaning in a diamond.
     */
    @Test
    void rejectsConflictingNamedParameterBindings() {
        String sql = "select VALUE from ITEM where FIRST_VALUE = :first and SECOND_VALUE = :second";
        TypeInfo left = repository("example.LeftRepository",
                                   method("find", TypeNames.STRING, sql, "first", "second"));
        TypeInfo right = repository("example.RightRepository",
                                    method("find", TypeNames.STRING, sql, "second", "first"));
        TypeInfo repository = repository("example.Repository", left, right);

        CodegenException failure = assertThrows(CodegenException.class,
                                                () -> JdbcTypeHierarchy.abstractMethods(repository,
                                                                                       new TypesRoundContext(Map.of())));

        assertThat(failure.getMessage(),
                   is("Inherited repository method 'find(java.lang.String,java.lang.String)' has incompatible named SQL "
                              + "parameter bindings."));
    }

    /**
     * Verifies the closest declaration can replace inherited JDBC policy.
     */
    @Test
    void usesTheClosestRepositoryDeclaration() {
        TypeInfo parent = repository("example.ParentRepository",
                                     method("find", TypeNames.STRING, "select OLD_VALUE from ITEM"));
        TypedElementInfo childMethod = method("find", TypeNames.STRING, "select NEW_VALUE from ITEM");
        TypeInfo repository = TypeInfo.builder()
                .typeName(TypeName.create("example.Repository"))
                .kind(ElementKind.INTERFACE)
                .addElementInfo(childMethod)
                .addInterfaceTypeInfo(parent)
                .build();

        List<TypedElementInfo> methods = JdbcTypeHierarchy.abstractMethods(repository,
                                                                          new TypesRoundContext(Map.of()));

        assertThat(methods.size(), is(1));
        assertThat(methods.getFirst()
                           .findAnnotation(JdbcCodegenTypes.JDBC_STATEMENT)
                           .flatMap(Annotation::stringValue)
                           .orElseThrow(),
                   is("select NEW_VALUE from ITEM"));
    }

    /**
     * Verifies repository return type covariance can use types generated during
     * the current code generation round.
     */
    @Test
    void resolvesRepositoryCovarianceFromTheCurrentRound() {
        TypeName baseResultType = TypeName.create("example.GeneratedBaseResult");
        TypeInfo baseResult = TypeInfo.builder()
                .typeName(baseResultType)
                .kind(ElementKind.CLASS)
                .build();
        TypeName childResultType = TypeName.create("example.GeneratedChildResult");
        TypeInfo childResult = TypeInfo.builder()
                .typeName(childResultType)
                .kind(ElementKind.CLASS)
                .superTypeInfo(baseResult)
                .build();
        String sql = "select VALUE from ITEM";
        TypeInfo baseRepository = repository("example.BaseRepository",
                                             method("find", baseResultType, sql));
        TypeInfo childRepository = repository("example.ChildRepository",
                                              method("find", childResultType, sql));
        TypeInfo repository = repository("example.Repository", baseRepository, childRepository);

        List<TypedElementInfo> methods = JdbcTypeHierarchy.abstractMethods(
                repository,
                new TypesRoundContext(Map.of(baseResultType, baseResult,
                                              childResultType, childResult)));

        assertThat(methods.size(), is(1));
        assertThat(methods.getFirst().typeName(), is(childResultType));
    }

    private static TypeInfo repository(String typeName, TypedElementInfo method) {
        return TypeInfo.builder()
                .typeName(TypeName.create(typeName))
                .kind(ElementKind.INTERFACE)
                .addElementInfo(method)
                .build();
    }

    private static TypeInfo repository(String typeName, TypeInfo... parents) {
        return TypeInfo.builder()
                .typeName(TypeName.create(typeName))
                .kind(ElementKind.INTERFACE)
                .interfaceTypeInfo(List.of(parents))
                .build();
    }

    private static TypedElementInfo method(String name, TypeName returnType, String sql, String... parameters) {
        TypedElementInfo.Builder builder = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName(name)
                .typeName(returnType)
                .addElementModifier(Modifier.ABSTRACT)
                .addAnnotation(Annotation.builder()
                                       .typeName(JdbcCodegenTypes.JDBC_STATEMENT)
                                       .value(sql)
                                       .build());
        for (String parameter : parameters) {
            builder.addParameterArgument(TypedElementInfo.builder()
                                                 .kind(ElementKind.PARAMETER)
                                                 .elementName(parameter)
                                                 .typeName(TypeNames.STRING)
                                                 .build());
        }
        return builder.build();
    }

    /**
     * Minimal round context for repository hierarchy policy tests.
     */
    private record TypesRoundContext(Map<TypeName, TypeInfo> typeInfo) implements RoundContext {
        @Override
        public Collection<TypeName> availableAnnotations() {
            return List.of();
        }

        @Override
        public Collection<TypeInfo> types() {
            return typeInfo.values();
        }

        @Override
        public Collection<TypeInfo> annotatedTypes(TypeName annotationType) {
            return List.of();
        }

        @Override
        public Collection<TypedElementInfo> annotatedElements(TypeName annotationType) {
            return List.of();
        }

        @Override
        public void addGeneratedType(TypeName type,
                                     ClassModel.Builder newClass,
                                     TypeName mainTrigger,
                                     Object... originatingElements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ClassModel.Builder> generatedType(TypeName type) {
            return Optional.empty();
        }

        @Override
        public Optional<TypeInfo> typeInfo(TypeName typeName) {
            return Optional.ofNullable(typeInfo.get(typeName));
        }
    }
}
