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
package io.helidon.data.jdbc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.common.Api;

/**
 * Declares JDBC behavior for Helidon Data repository methods.
 * <p>
 * Each repository method supplies one SQL statement and may specify how the
 * statement is executed or mapped. Code generation reads these annotations,
 * the JDBC provider does not inspect them at runtime.
 */
@Api.Preview
public final class Jdbc {

    // Use one provider identifier for repository selection, service qualifiers, and transaction lifecycle events.
    static final String PROVIDER = "jdbc";

    /**
     * Prevents construction.
     */
    private Jdbc() {
        throw new UnsupportedOperationException("The Jdbc class cannot be instantiated.");
    }

    /**
     * Declares the SQL statement executed by a repository method.
     * <p>
     * SQL statement may use named markers matching Java parameter names or
     * positional JDBC markers. A statement must use one style consistently.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Statement {

        /**
         * Returns the SQL statement.
         *
         * @return SQL statement
         */
        String value();
    }

    /**
     * Selects how a repository statement is executed.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Execution {

        /**
         * Returns the requested execution type.
         *
         * @return execution type
         */
        ExecutionType value() default ExecutionType.AUTO;
    }

    /**
     * Execution choices supported by the JDBC repository provider.
     */
    public enum ExecutionType {
        /**
         * Allows code generation infer query or update behavior from the method
         * signature and its JDBC annotations.
         */
        AUTO,

        /**
         * Executes the statement as a query and maps its rows.
         */
        QUERY,

        /**
         * Executes the statement as an update.
         */
        UPDATE
    }

    /**
     * Requests generated keys from an update statement.
     * <p>
     * An empty value uses the default generated keys by the JDBC driver.
     * Otherwise, the provided values are passed to JDBC in declaration order.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.SOURCE)
    public @interface GeneratedKeys {

        /**
         * Returns the requested generated column names.
         *
         * @return column names, or an empty array to use the driver defaults
         */
        String[] value() default {};
    }

    /**
     * Selects an application row mapper for query or generated key rows.
     * <p>
     * With a mapper class, generated code injects that exact service type.
     * Without a class, generated code requires a row mapper service whose
     * generic result type exactly matches the repository result type. When this
     * annotation is absent, supported records use generated mapping.
     * <p>
     * A mapper used by a singleton repository must be stateless or safe for
     * concurrent use. The mapper receives a scoped {@link JdbcClient.Row} and
     * never owns JDBC resources.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.SOURCE)
    public @interface RowMapper {

        /**
         * Returns the mapper service type.
         *
         * @return service type, or {@link Void} to select by generic result type
         */
        Class<?> value() default Void.class;
    }
}
