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

import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.tests.application.EqualWeightContact;
import io.helidon.service.registry.Service;

/**
 * Alphabetically first of two equal-weight mapper services.
 */
@Service.Singleton
public final class AlphaEqualWeightMapper implements JdbcClient.RowMapper<EqualWeightContact> {
    @Override
    public EqualWeightContact map(JdbcClient.Row row) {
        return new EqualWeightContact("alpha:" + row.get(1, String.class));
    }
}
