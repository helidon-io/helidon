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
package io.helidon.tests.integration.data.jdbc.declarative;

import java.util.Objects;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Service;
import io.helidon.tests.integration.data.jdbc.application.ContactLabel;

/**
 * Lower-weight mapper proving deterministic marker-service preference.
 */
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
public final class AlternateContactMapper implements JdbcClient.RowMapper<ContactLabel> {
    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if row is null
     */
    @Override
    public ContactLabel map(JdbcClient.Row row) {
        Objects.requireNonNull(row, "The JDBC row must not be null.");
        return new ContactLabel(row.get("ID", Long.class),
                                "alternate:" + row.get("NAME", String.class));
    }
}
