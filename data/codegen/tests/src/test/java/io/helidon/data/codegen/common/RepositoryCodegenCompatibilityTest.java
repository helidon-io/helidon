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
package io.helidon.data.codegen.common;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.common.spi.RepositoryGenerator;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryCodegenCompatibilityTest {

    private static final TypeName REPOSITORY_ANNOTATION = TypeName.create("example.Repository");
    private static final TypeName REPOSITORY_TYPE = TypeName.create("example.TestRepository");

    @Test
    void preservesDefaultAndExplicitProviderSelection() {
        TypeInfo unqualified = annotationOnlyRepository(null);
        TypeInfo explicitlySelected = annotationOnlyRepository("jdbc");
        CodegenContext context = mock(CodegenContext.class);
        RoundContext round = mock(RoundContext.class);
        RepositoryGenerator repositoryGenerator = mock(RepositoryGenerator.class);
        TypeInfo placeholder = TypeInfo.builder()
                .typeName(TypeName.create(Object.class))
                .kind(ElementKind.CLASS)
                .build();
        when(repositoryGenerator.createRepositoryInfo(any(), eq(context)))
                .thenAnswer(invocation -> new RepositoryInfo(invocation.getArgument(0),
                                                             Map.of(),
                                                             placeholder,
                                                             TypeName.create(Object.class)));

        RecordingPersistenceGenerator defaultProvider = new RecordingPersistenceGenerator("legacy", true);
        RecordingPersistenceGenerator optInProvider = new RecordingPersistenceGenerator("jdbc", false);

        defaultProvider.generate(context, round, unqualified, repositoryGenerator);
        optInProvider.generate(context, round, unqualified, repositoryGenerator);
        assertThat(defaultProvider.generated(), is(1));
        assertThat(optInProvider.generated(), is(0));

        defaultProvider.generate(context, round, explicitlySelected, repositoryGenerator);
        optInProvider.generate(context, round, explicitlySelected, repositoryGenerator);
        assertThat(defaultProvider.generated(), is(1));
        assertThat(optInProvider.generated(), is(1));
    }

    @Test
    void invokesDirectSpiImplementationsThroughGeneratorFanOut() {
        TypeInfo repository = annotationOnlyRepository("jdbc");
        RepositoryGenerator owner = generator(Set.of(REPOSITORY_ANNOTATION), Set.of());
        DirectPersistenceGenerator first = new DirectPersistenceGenerator();
        DirectPersistenceGenerator second = new DirectPersistenceGenerator();
        RoundContext round = round(repository);
        CodegenContext context = mock(CodegenContext.class);

        new RepositoryCodegen(context, List.of(owner), List.of(first, second)).process(round);

        assertThat(first.generated(), is(1));
        assertThat(second.generated(), is(1));
    }

    @Test
    void requiresProviderForRepositoryWithoutSupportedBaseInterface() {
        TypeInfo repository = annotationOnlyRepository(null);
        RepositoryGenerator owner = generator(Set.of(REPOSITORY_ANNOTATION), Set.of());
        DirectPersistenceGenerator persistence = new DirectPersistenceGenerator();

        CodegenException failure = assertThrows(
                CodegenException.class,
                () -> new RepositoryCodegen(mock(CodegenContext.class), List.of(owner), List.of(persistence))
                        .process(round(repository)));

        assertThat(failure.getMessage(), containsString("must declare @Data.Provider"));
        assertThat(persistence.generated(), is(0));
    }

    @Test
    void selectsTheSoleAnnotationOwner() {
        TypeInfo repository = annotationOnlyRepository("jdbc");
        RepositoryGenerator owner = generator(Set.of(REPOSITORY_ANNOTATION), Set.of());
        PersistenceGenerator persistence = mock(PersistenceGenerator.class);
        RoundContext round = round(repository);
        CodegenContext context = mock(CodegenContext.class);

        new RepositoryCodegen(context, List.of(owner), List.of(persistence)).process(round);

        verify(persistence).generate(context, round, repository, owner);
    }

    @Test
    void rejectsAbsentAndAmbiguousAnnotationOwnership() {
        TypeInfo repository = annotationOnlyRepository("jdbc");
        PersistenceGenerator persistence = mock(PersistenceGenerator.class);
        CodegenContext context = mock(CodegenContext.class);

        RepositoryGenerator unrelated = generator(Set.of(REPOSITORY_ANNOTATION), Set.of());
        TypeInfo repositoryWithoutOwnedAnnotation = TypeInfo.builder()
                .typeName(REPOSITORY_TYPE)
                .kind(ElementKind.INTERFACE)
                .build();
        CodegenException absent = assertThrows(
                CodegenException.class,
                () -> new RepositoryCodegen(context, List.of(unrelated), List.of(persistence))
                        .process(round(repositoryWithoutOwnedAnnotation)));
        assertThat(absent.getMessage(), containsString("extends no data repository provider's interface"));
        verify(persistence, never()).generate(any(), any(), any(), any());

        RepositoryGenerator first = generator(Set.of(REPOSITORY_ANNOTATION), Set.of());
        RepositoryGenerator second = generator(Set.of(REPOSITORY_ANNOTATION), Set.of());
        CodegenException ambiguous = assertThrows(
                CodegenException.class,
                () -> new RepositoryCodegen(context, List.of(first, second), List.of(persistence))
                        .process(round(repository)));
        assertThat(ambiguous.getMessage(), containsString("owned by multiple data repository generators"));
    }

    @Test
    void preservesEntityInterfaceOwnership() {
        TypeName entityRepository = TypeName.create("example.EntityRepository");
        TypeInfo base = TypeInfo.builder()
                .typeName(entityRepository)
                .kind(ElementKind.INTERFACE)
                .build();
        TypeInfo repository = TypeInfo.builder()
                .typeName(REPOSITORY_TYPE)
                .kind(ElementKind.INTERFACE)
                .addInterfaceTypeInfo(base)
                .build();
        RepositoryGenerator owner = generator(Set.of(REPOSITORY_ANNOTATION), Set.of(entityRepository));
        PersistenceGenerator persistence = mock(PersistenceGenerator.class);
        RoundContext round = round(repository);
        CodegenContext context = mock(CodegenContext.class);

        new RepositoryCodegen(context, List.of(owner), List.of(persistence)).process(round);

        verify(persistence).generate(context, round, repository, owner);
    }

    private static RepositoryGenerator generator(Set<TypeName> annotations, Set<TypeName> interfaces) {
        RepositoryGenerator generator = mock(RepositoryGenerator.class);
        when(generator.annotations()).thenReturn(annotations);
        when(generator.interfaces()).thenReturn(interfaces);
        return generator;
    }

    private static RoundContext round(TypeInfo repository) {
        RoundContext round = mock(RoundContext.class);
        when(round.annotatedTypes(any())).thenReturn(List.of(repository));
        return round;
    }

    private static TypeInfo annotationOnlyRepository(String provider) {
        TypeInfo.Builder builder = TypeInfo.builder()
                .typeName(REPOSITORY_TYPE)
                .kind(ElementKind.INTERFACE)
                .addAnnotation(Annotation.create(REPOSITORY_ANNOTATION));
        if (provider != null) {
            builder.addAnnotation(Annotation.builder()
                                          .typeName(DataCommonCodegenTypes.PROVIDER)
                                          .value(provider)
                                          .build());
        }
        return builder.build();
    }

    private static final class DirectPersistenceGenerator implements PersistenceGenerator {

        private int generated;

        @Override
        public void generate(CodegenContext codegenContext,
                             RoundContext roundContext,
                             TypeInfo repository,
                             RepositoryGenerator repositoryGenerator) {
            generated++;
        }

        @Override
        public QueryBuilder queryBuilder(RepositoryInfo repositoryInfo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StatementGenerator statementGenerator() {
            throw new UnsupportedOperationException();
        }

        private int generated() {
            return generated;
        }
    }

    private static final class RecordingPersistenceGenerator extends BasePersistenceGenerator {

        private final String provider;
        private final boolean generateByDefault;
        private int generated;

        private RecordingPersistenceGenerator(String provider, boolean generateByDefault) {
            this.provider = provider;
            this.generateByDefault = generateByDefault;
        }

        @Override
        protected String provider() {
            return provider;
        }

        @Override
        protected boolean generateByDefault() {
            return generateByDefault;
        }

        @Override
        protected TypeName repositoryClassName(TypeName baseName) {
            return TypeName.builder(baseName).className(baseName.className() + "__Generated").build();
        }

        @Override
        protected void generateRepositoryClass(CodegenContext codegenContext,
                                               RoundContext roundContext,
                                               RepositoryGenerator repositoryGenerator,
                                               RepositoryInfo repositoryInfo,
                                               TypeName className,
                                               ClassModel.Builder classModel) {
            generated++;
        }

        @Override
        public QueryBuilder queryBuilder(RepositoryInfo repositoryInfo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StatementGenerator statementGenerator() {
            throw new UnsupportedOperationException();
        }

        private int generated() {
            return generated;
        }
    }
}
