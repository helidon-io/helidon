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
package io.helidon.data.sql.common;

import io.helidon.builder.api.Prototype;
import io.helidon.data.DataException;

final class SqlConfigSupport {

    private SqlConfigSupport() {
        throw new UnsupportedOperationException("No instances of SqlConfigSupport are allowed");
    }

    static class Decorator implements Prototype.BuilderDecorator<SqlConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(SqlConfig.BuilderBase<?, ?> target) {
            if (target.connection().isEmpty()) {
                if (target.dataSource().isEmpty()) {
                    throw new DataException("Both connection and DataSource config options are missing");
                }
            } else {
                if (target.dataSource().isPresent()) {
                    throw new DataException("Both connection and DataSource config options are present");
                }
            }
        }
    }
}
