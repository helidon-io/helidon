/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
 * A useful functional interface whose implementations can perform work that may throw a {@link SQLException}.
 *
 * @param <T> the type of the object supplied
 *
 * @see #get()
 */
@FunctionalInterface
public interface SQLSupplier<T> {

    /**
     * Performs work and returns the result.
     *
     * @return the result of the work
     *
     * @exception SQLException if a database access error occurs
     */
    T get() throws SQLException;

}
