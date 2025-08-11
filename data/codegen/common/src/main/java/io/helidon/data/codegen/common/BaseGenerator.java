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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Consumer;

import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

/**
 * Base code generator.
 * Common utilities for both repository interface and persistence provider generators.
 */
public abstract class BaseGenerator {
    /**
     * Name of the generic type {@code T}.
     */
    protected static final String GENERIC_T = "T";
    /**
     * Type of the generic type {@code T}.
     */
    protected static final TypeName T = TypeName.create(GENERIC_T);
    /**
     * Local entity variable.
     */
    protected static final String ENTITY = "entity";
    /**
     * Local entities Collection variable.
     */
    protected static final String ENTITIES = "entities";
    /**
     * Type of the {@code Iterable<T>}.
     */
    protected static final TypeName ITERABLE_T = TypeName.builder()
            .type(Iterable.class)
            .addTypeArgument(T)
            .build();
    /**
     * Type of the {@code Object}.
     */
    protected static final TypeName OBJECT = TypeName.create(Object.class);
    /**
     * {@code Iterable<T> entities} method parameter.
     */
    protected static final Parameter ITERABLE_T_ENTITIES = Parameter.builder()
            .name(ENTITIES)
            .type(ITERABLE_T)
            .build();
    /**
     * Type of the {@code List<T>}.
     */
    protected static final TypeName LIST_T = TypeName.builder()
            .type(List.class)
            .addTypeArgument(T)
            .build();
    /**
     * {@code List<T> entities} method parameter.
     */
    protected static final Parameter LIST_T_ENTITIES = Parameter.builder()
            .name(ENTITIES)
            .type(LIST_T)
            .build();
    /**
     * {@code T entity} method parameter.
     */
    protected static final Parameter T_ENTITY = Parameter.builder()
            .name(ENTITY)
            .type(T)
            .build();
    /**
     * Name of the generic type {@code ?}.
     */
    protected static final String GENERIC_WILDCARD = "?";
    /**
     * Local executor variable.
     */
    protected static final String EXECUTOR = "executor";
    /**
     * Local id variable.
     */
    protected static final String ID = "id";
    /**
     * {@link Number} type.
     */
    protected static final TypeName NUMBER = TypeName.create(Number.class);
    /**
     * {@link BigInteger} type.
     */
    protected static final TypeName BIG_INTEGER = TypeName.create(BigInteger.class);
    /**
     * {@link BigDecimal} type.
     */
    protected static final TypeName BIG_DECIMAL = TypeName.create(BigDecimal.class);
    /**
     * Type of the {@code Class<?>}.
     */
    protected static final TypeName CLASS_WILDCARD = TypeName.builder()
            .type(Class.class)
            .addTypeArgument(TypeNames.WILDCARD)
            .build();

    /**
     * Create an instance of generator base class.
     */
    protected BaseGenerator() {
    }

    /*
     * Design note:
     * Current Java AST expanding methods are based on Method.Builder lambda expressions. This was easy
     * to implement to get initial version of the codegen. But it's not very effective.
     * For next versions Method.Builder should be class instance property shared by all implementing methods.
     * This should slightly simplify the code and replace lambdas with method references in some cases.
     *
     * Also, this AST methods syntax does not cover whole code being generated. This may be finished in later
     * versions.
     */

    /**
     * Generate statement.
     *
     * @param builder method builder
     * @param content additional statement content
     */
    protected static void statement(Method.Builder builder, Consumer<Method.Builder> content) {
        content.accept(builder);
        builder.addContentLine(";");
    }

    /**
     * Generate return statement.
     *
     * @param builder method builder
     * @param content additional statement content
     */
    protected static void returnStatement(Method.Builder builder, Consumer<Method.Builder> content) {
        builder.addContent("return ");
        content.accept(builder);
        builder.addContentLine(";");
    }

    /**
     * Generate identifier.
     *
     * @param builder    method builder
     * @param identifier identifier name
     */
    protected static void identifier(Method.Builder builder, String identifier) {
        builder.addContent(identifier);
    }

    /**
     * Generate value.
     *
     * @param builder method builder
     * @param value   identifier name
     */
    protected static void value(Method.Builder builder, String value) {
        builder.addContent(value);
    }

    /**
     * Generate initialized variable.
     *
     * @param builder method builder
     * @param type variable type
     * @param name  variable name
     * @param value variable value content
     */
    protected static void initializedVariable(Method.Builder builder,
                                              TypeName type,
                                              String name,
                                              Consumer<Method.Builder> value) {
        builder.addContent(type)
                .addContent(" ")
                .addContent(name)
                .addContent(" = ");
        value.accept(builder);
    }

    /**
     * Generate {@code null} value.
     *
     * @param builder method builder
     */
    protected static void nullValue(Method.Builder builder) {
        builder.addContent("null");
    }

    /**
     * Generate {@code throw new <type>(<message>)} for an exception.
     *
     * @param builder method builder
     * @param type    exception type
     * @param message exception message
     */
    protected static void throwException(Method.Builder builder, TypeName type, String message) {
        builder.addContent("throw new ")
                .addContent(type)
                .addContent("(\"")
                .addContent(message)
                .addContent("\")");
    }

    /**
     * Generate {@code throw new <type>(<message>)} for an exception.
     *
     * @param builder method builder
     * @param type    exception type
     * @param message exception message content
     */
    protected static void throwException(Method.Builder builder, TypeName type, Consumer<Method.Builder> message) {
        builder.addContent("throw new ")
                .addContent(type)
                .addContent("(");
        message.accept(builder);
        builder.addContent(")");
    }

    /**
     * Increase padding.
     *
     * @param builder method builder
     * @param count   number of paddings to add
     */
    protected static void increasePadding(Method.Builder builder, int count) {
        for (int i = 0; i < count; i++) {
            builder.increaseContentPadding();
        }
    }

    /**
     * Decrease padding.
     *
     * @param builder method builder
     * @param count   number of paddings to remove
     */
    protected static void decreasePadding(Method.Builder builder, int count) {
        for (int i = 0; i < count; i++) {
            builder.decreaseContentPadding();
        }
    }

}
