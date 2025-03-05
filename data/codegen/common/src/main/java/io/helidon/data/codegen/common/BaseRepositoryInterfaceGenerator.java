/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;

/**
 * Data repository interface code generator base class.
 */
public abstract class BaseRepositoryInterfaceGenerator
        extends BaseGenerator
        implements RepositoryInterfaceGenerator {

    private final RepositoryInfo repositoryInfo;
    // Target class builder
    private final ClassModel.Builder classModel;
    private final CodegenContext codegenContext;
    // Persistence provider specific generator
    private final PersistenceGenerator persistenceGenerator;

    private final PersistenceGenerator.QueryBuilder queryBuilder;
    private final PersistenceGenerator.StatementGenerator statementGenerator;

    /**
     * Creates an instance of data repository interface code generator base class.
     *
     * @param repositoryInfo data repository interface info
     * @param classModel target class builder
     * @param codegenContext code processing and generation context
     * @param persistenceGenerator persistence provider specific generator
     */
    protected BaseRepositoryInterfaceGenerator(RepositoryInfo repositoryInfo,
                                               ClassModel.Builder classModel,
                                               CodegenContext codegenContext,
                                               PersistenceGenerator persistenceGenerator) {
        super();
        this.repositoryInfo = repositoryInfo;
        this.classModel = classModel;
        this.codegenContext = codegenContext;
        this.persistenceGenerator = persistenceGenerator;
        this.queryBuilder = persistenceGenerator().queryBuilder(repositoryInfo);
        this.statementGenerator = persistenceGenerator().statementGenerator();
    }


    /**
     * Data repository interface info.
     *
     * @return data repository interface descriptor
     */
    protected RepositoryInfo repositoryInfo() {
        return repositoryInfo;
    }

    /**
     * Target implementing class model builder.
     *
     * @return class model builder
     */
    protected ClassModel.Builder classModel() {
        return classModel;
    }

    /**
     * Code processing and generation context.
     *
     * @return codegen context
     */
    protected CodegenContext codegenContext() {
        return codegenContext;
    }

    /**
     * Specific persistence provider generator.
     * Repository interface implementing class is defined by {@link PersistenceGenerator}
     * but it content (e.g. implemented data repository interfaces) are defined
     * by {@link io.helidon.data.codegen.common.spi.RepositoryGenerator} which holds individual
     * instances of {@link RepositoryInterfaceGenerator} classes.
     * {@link PersistenceGenerator} provides persistence provider specific code for data
     * repository interface implementing code.
     *
     * @return persistence provider generator
     */
    protected PersistenceGenerator persistenceGenerator() {
        return persistenceGenerator;
    }

    /**
     * Query code builder from persistence provider generator.
     *
     * @return query code builder
     */
    protected PersistenceGenerator.QueryBuilder queryBuilder() {
        return queryBuilder;
    }

    /**
     * Persistence provider specific code snippets generator.
     *
     * @return code snippets generator
     */
    protected PersistenceGenerator.StatementGenerator statementGenerator() {
        return statementGenerator;
    }

    /**
     * Log method generation warning.
     *
     * @param methodInfo method info
     * @param message warning message
     */
    protected void methodWarning(TypedElementInfo methodInfo, String message) {
        codegenContext().logger()
                .log(Level.WARNING,
                     String.format("%s: %s", methodPrototypeString(methodInfo), message));
    }

    /**
     * Generate {@link java.util.Optional#ofNullable(Object)} call.
     *
     * @param builder method builder
     * @param content additional statement content
     */
    protected static void optionalOfNullable(Method.Builder builder, Consumer<Method.Builder> content) {
        builder.addContent(Optional.class)
                .addContent(".ofNullable(");
        content.accept(builder);
        builder.addContent(")");
    }

    /**
     * Generate {@code  <T> Optional<T> RepositoryExecutor#optionalFromQuery(List<T>)} call.
     * This is Jakarta Persistence 3.1 workaround.
     *
     * @param builder method builder
     * @param content additional statement content
     * @deprecated will be removed with Jakarta Persistence 3.2
     */
    @Deprecated
    protected static void optionalFromQuery(Method.Builder builder, Consumer<Method.Builder> content) {
        builder.addContent(TypeName.create(
                        "io.helidon.data.jakarta.persistence.gapi.JpaRepositoryExecutor"))
                .addContent(".optionalFromQuery(");
        content.accept(builder);
        builder.addContent(")");
    }

    // Common patterns of generated code

    /**
     * Generate {@code Callable} call.
     *
     * @param builder method builder
     * @param content additional statement content
     * @param identifier {@code Callable} instance identifier
     */
    protected static void call(Method.Builder builder, Consumer<Method.Builder> content, String identifier) {
        builder.addContent(identifier)
                .addContent(".call(");
        content.accept(builder);
        builder.addContent(")");
    }

    /**
     * Generate {@code Runnable} run.
     *
     * @param builder method builder
     * @param content additional statement content
     * @param identifier {@code Callable} instance identifier
     */
    protected static void run(Method.Builder builder, Consumer<Method.Builder> content, String identifier) {
        builder.addContent(identifier)
                .addContent(".run(");
        content.accept(builder);
        builder.addContent(")");
    }

    /**
     * Generate {@code <type> extends <extended>} generic type.
     *
     * @param type extending type
     * @param extended type being extended
     * @return {@code extends} generic type
     */
    protected static TypeArgument extendsType(String type, TypeName extended) {
        return TypeArgument.builder()
                .bound(extended)
                .token(type)
                .build();
    }

    private static String methodPrototypeString(TypedElementInfo methodInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append(methodInfo.elementName())
                .append('(');
        boolean first = true;
        for (TypedElementInfo info : methodInfo.parameterArguments()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(info.typeName().className());
        }
        sb.append(")");
        return sb.toString();
    }

}
