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

package io.helidon.data.sql.common;

import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.data.DataException;

/**
 * Connection configuration support.
 */
final class ConnectionConfigSupport {

    private ConnectionConfigSupport() {
    }

    /**
     * Validates connection configuration before generated required-property validation.
     */
    static final class Decorator implements Prototype.BuilderDecorator<ConnectionConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(ConnectionConfig.BuilderBase<?, ?> target) {
            Optional<String> url = target.url();
            if (url.isEmpty()) {
                return;
            }
            if (url.filter(String::isEmpty).isPresent()) {
                throw new DataException("The database connection URL must not be empty.");
            }
            if (target.jdbcDriverClassName().filter(String::isEmpty).isPresent()) {
                throw new DataException("The JDBC driver class name must not be empty.");
            }
        }
    }
}
