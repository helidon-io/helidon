/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.integrations.jdbc;

import java.sql.SQLException;

/**
 * A {@link RuntimeException} that wraps a {@link SQLException}.
 */
public final class UncheckedSQLException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new {@link UncheckedSQLException}.
     *
     * @param cause the {@link SQLException} that this {@link UncheckedSQLException} will represent; may be {@code null}
     */
    public UncheckedSQLException(SQLException cause) {
        super(cause);
    }

    /**
     * Returns the {@link SQLException} this {@link UncheckedSQLException} represents.
     *
     * <p>This method may return {@code null}.</p>
     *
     * @return the {@link SQLException} this {@link UncheckedSQLException} represents, or {@code null}
     */
    @Override
    public SQLException getCause() {
        return (SQLException) super.getCause();
    }

}
