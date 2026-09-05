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
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcMethodPlanRoundContextTest {

    @Test
    void acceptsARecordAvailableOnlyFromTheCurrentRound() {
        TypeName recordType = TypeName.create("example.GeneratedRecord");
        TypeInfo recordInfo = TypeInfo.builder()
                .typeName(recordType)
                .kind(ElementKind.RECORD)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addElementInfo(component -> component.kind(ElementKind.RECORD_COMPONENT)
                        .elementName("value")
                        .typeName(TypeNames.STRING))
                .build();

        JdbcMethodPlan plan = JdbcMethodPlan.create(repositoryMethod("recordValue", recordType, null),
                                                    new TypesRoundContext(Map.of(recordType, recordInfo)));

        assertThat(plan.mappingKind(), is(JdbcMethodPlan.MappingKind.RECORD));
        assertThat(plan.recordComponents().size(), is(1));
    }

    /**
     * Verifies a concrete use of a round-visible generic record resolves its
     * formal component type before JDBC validation and generation.
     */
    @Test
    void resolvesAParameterizedRecordAvailableOnlyFromTheCurrentRound() {
        TypeName variable = TypeName.createFromGenericDeclaration("T");
        TypeName recordType = TypeName.create("example.GeneratedRecord");
        TypeName mappedType = TypeName.builder(recordType)
                .addTypeArgument(TypeNames.STRING)
                .build();
        TypeInfo recordInfo = TypeInfo.builder()
                .typeName(TypeName.builder(recordType)
                                  .addTypeArgument(variable)
                                  .build())
                .rawType(recordType)
                .declaredType(TypeName.builder(recordType)
                                      .addTypeParameter("T")
                                      .addTypeArgument(variable)
                                      .build())
                .kind(ElementKind.RECORD)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addElementInfo(component -> component.kind(ElementKind.RECORD_COMPONENT)
                        .elementName("value")
                        .typeName(variable))
                .build();

        JdbcMethodPlan plan = JdbcMethodPlan.create(repositoryMethod("recordValue", mappedType, null),
                                                    new TypesRoundContext(Map.of(mappedType, recordInfo)));

        assertThat(plan.mappingKind(), is(JdbcMethodPlan.MappingKind.RECORD));
        assertThat(plan.recordComponents().getFirst().typeName(), is(TypeNames.STRING));
    }

    @Test
    void acceptsAnExplicitMapperAvailableOnlyFromTheCurrentRound() {
        TypeName mapperType = TypeName.create("example.GeneratedStringMapper");
        TypeName mapperContract = TypeName.builder(JdbcCodegenTypes.ROW_MAPPER)
                .addTypeArgument(TypeNames.STRING)
                .build();
        TypeInfo mapperInfo = TypeInfo.builder()
                .typeName(mapperType)
                .kind(ElementKind.CLASS)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addInterfaceTypeInfo(interfaceInfo -> interfaceInfo.typeName(mapperContract)
                        .kind(ElementKind.INTERFACE))
                .build();

        JdbcMethodPlan plan = JdbcMethodPlan.create(repositoryMethod("mappedValue", TypeNames.STRING, mapperType),
                                                    new TypesRoundContext(Map.of(mapperType, mapperInfo)));

        assertThat(plan.mappingKind(), is(JdbcMethodPlan.MappingKind.EXPLICIT));
        assertThat(plan.explicitMapper(), is(mapperType));
    }

    @Test
    void treatsTheRawMapperContractAsMarkerSelection() {
        JdbcMethodPlan plan = JdbcMethodPlan.create(
                repositoryMethod("mappedValue", TypeNames.STRING, JdbcCodegenTypes.ROW_MAPPER),
                new TypesRoundContext(Map.of()));

        assertThat(plan.mappingKind(), is(JdbcMethodPlan.MappingKind.SERVICE));
        assertThat(plan.explicitMapper(), nullValue());
    }

    /**
     * Verifies nested generic arguments participate in exact explicit-mapper contract validation.
     */
    @Test
    void rejectsAnExplicitMapperWithDifferentNestedTypeArguments() {
        TypeName boxType = TypeName.create("example.Box");
        TypeName stringBoxType = TypeName.builder(boxType)
                .addTypeArgument(TypeNames.STRING)
                .build();
        TypeName integerBoxType = TypeName.builder(boxType)
                .addTypeArgument(TypeNames.BOXED_INT)
                .build();
        TypeName mapperType = TypeName.create("example.GeneratedIntegerBoxMapper");
        TypeName mapperContract = TypeName.builder(JdbcCodegenTypes.ROW_MAPPER)
                .addTypeArgument(integerBoxType)
                .build();
        TypeInfo mapperInfo = TypeInfo.builder()
                .typeName(mapperType)
                .kind(ElementKind.CLASS)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addInterfaceTypeInfo(interfaceInfo -> interfaceInfo.typeName(mapperContract)
                        .kind(ElementKind.INTERFACE))
                .build();

        CodegenException failure = assertThrows(
                CodegenException.class,
                () -> JdbcMethodPlan.create(repositoryMethod("mappedValue", stringBoxType, mapperType),
                                            new TypesRoundContext(Map.of(mapperType, mapperInfo))));

        assertThat(failure.getMessage(),
                   is("The mapper must implement JdbcClient.RowMapper<example.Box<java.lang.String>>."));
    }

    /**
     * Verifies an explicit mapper can inherit its generic row mapper contract
     * through a round visible superclass.
     */
    @Test
    void acceptsAnInheritedMapperContractFromTheCurrentRound() {
        TypeName variable = TypeName.createFromGenericDeclaration("T");
        TypeName baseType = TypeName.create("example.GeneratedBaseMapper");
        TypeInfo baseInfo = TypeInfo.builder()
                .typeName(baseType)
                .declaredType(TypeName.builder(baseType)
                                      .addTypeParameter("T")
                                      .addTypeArgument(variable)
                                      .build())
                .kind(ElementKind.CLASS)
                .addInterfaceTypeInfo(interfaceInfo -> interfaceInfo
                        .typeName(TypeName.builder(JdbcCodegenTypes.ROW_MAPPER)
                                          .addTypeArgument(variable)
                                          .build())
                        .kind(ElementKind.INTERFACE))
                .build();
        TypeName mapperType = TypeName.create("example.GeneratedStringMapper");
        TypeInfo mapperInfo = TypeInfo.builder()
                .typeName(mapperType)
                .kind(ElementKind.CLASS)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .superTypeInfo(TypeInfo.builder(baseInfo)
                                       .typeName(TypeName.builder(baseType)
                                                         .addTypeArgument(TypeNames.STRING)
                                                         .build())
                                       .build())
                .build();

        JdbcMethodPlan plan = JdbcMethodPlan.create(repositoryMethod("mappedValue", TypeNames.STRING, mapperType),
                                                    new TypesRoundContext(Map.of(mapperType, mapperInfo)));

        assertThat(plan.mappingKind(), is(JdbcMethodPlan.MappingKind.EXPLICIT));
        assertThat(plan.explicitMapper(), is(mapperType));
    }

    @Test
    void reportsMissingAndBlankStatementAnnotationsAsCompleteSentences() {
        TypedElementInfo missingStatement = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName("missingStatement")
                .typeName(TypeNames.STRING)
                .enclosingType(TypeName.create("example.Repository"))
                .build();
        CodegenException missing = assertThrows(
                CodegenException.class,
                () -> JdbcMethodPlan.create(missingStatement, new TypesRoundContext(Map.of())));
        assertThat(missing.getMessage(),
                   is("An abstract JDBC repository method must declare @Jdbc.Statement."));

        CodegenException blank = assertThrows(
                CodegenException.class,
                () -> JdbcMethodPlan.create(repositoryMethod("blankStatement", TypeNames.STRING, null, "  "),
                                            new TypesRoundContext(Map.of())));
        assertThat(blank.getMessage(), is("The SQL statement declared by @Jdbc.Statement must not be blank."));
    }

    private static TypedElementInfo repositoryMethod(String name, TypeName returnType, TypeName mapperType) {
        return repositoryMethod(name, returnType, mapperType, "select VALUE from TEST_VALUE");
    }

    private static TypedElementInfo repositoryMethod(String name,
                                                     TypeName returnType,
                                                     TypeName mapperType,
                                                     String sql) {
        TypedElementInfo.Builder builder = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .elementName(name)
                .typeName(returnType)
                .enclosingType(TypeName.create("example.Repository"))
                .addAnnotation(Annotation.builder()
                                       .typeName(JdbcCodegenTypes.JDBC_STATEMENT)
                                       .value(sql)
                                       .build());
        if (mapperType != null) {
            builder.addAnnotation(Annotation.builder()
                                          .typeName(JdbcCodegenTypes.JDBC_ROW_MAPPER)
                                          .property("value", mapperType)
                                          .build());
        }
        return builder.build();
    }

    /**
     * Minimal round context whose type table represents classes registered by
     * an earlier codegen extension.
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
