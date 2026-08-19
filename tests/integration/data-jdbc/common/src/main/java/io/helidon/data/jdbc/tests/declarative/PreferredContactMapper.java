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
package io.helidon.data.jdbc.tests.declarative;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.tests.application.ContactLabel;
import io.helidon.service.registry.Service;

/**
 * Preferred marker mapper, selected by normal Service Registry weight rules.
 */
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT + 10)
public final class PreferredContactMapper implements JdbcClient.RowMapper<ContactLabel> {
    private final LabelPrefix prefix;

    /**
     * Creates the mapper with an application dependency.
     *
     * @param prefix label prefix service
     */
    @Service.Inject
    PreferredContactMapper(LabelPrefix prefix) {
        this.prefix = prefix;
    }

    @Override
    public ContactLabel map(JdbcClient.Row row) {
        return new ContactLabel(row.required("ID", Long.class),
                                prefix.apply(row.required("NAME", String.class)));
    }
}
