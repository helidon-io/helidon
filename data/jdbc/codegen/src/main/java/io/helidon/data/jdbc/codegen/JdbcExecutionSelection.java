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

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;

/**
 * Normalized public execution choices used during JDBC code generation.
 */
enum JdbcExecutionSelection {
    AUTO,
    QUERY,
    UPDATE,
    BATCH;

    /**
     * Resolves the execution choice after inherited types and controls have been resolved.
     * An omitted annotation or value selects {@link #AUTO}. An exact {@code JdbcClient.BatchResult}
     * return type selects {@link #BATCH} under AUTO unless mapping or generated keys select another operation.
     *
     * @param method The method supplies resolved types and effective annotations.
     * @return The result is the explicit choice or the normalized AUTO selection.
     */
    static JdbcExecutionSelection create(TypedElementInfo method) {
        String value = method.findAnnotation(JdbcCodegenTypes.JDBC_EXECUTION)
                .flatMap(Annotation::stringValue)
                .orElse(AUTO.name());
        try {
            JdbcExecutionSelection requested = JdbcExecutionSelection.valueOf(value);
            if (requested == AUTO
                    && method.hasAnnotation(JdbcCodegenTypes.JDBC_STATEMENT)
                    && !method.typeName().generic()
                    && !method.typeName().wildcard()
                    && method.typeName().equals(JdbcCodegenTypes.JDBC_BATCH_RESULT)
                    && !method.hasAnnotation(JdbcCodegenTypes.JDBC_ROW_MAPPER)
                    && !method.hasAnnotation(JdbcCodegenTypes.JDBC_GENERATED_KEYS)) {
                return BATCH;
            }
            return requested;
        } catch (IllegalArgumentException _) {
            throw new CodegenException("@Jdbc.Execution does not support the value '" + value + "'.",
                                       method.originatingElementValue());
        }
    }
}
