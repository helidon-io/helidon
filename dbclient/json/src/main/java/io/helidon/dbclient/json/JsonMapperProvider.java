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
package io.helidon.dbclient.json;

import java.util.Optional;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.spi.DbMapperProvider;
import io.helidon.json.JsonObject;

/**
 * Helidon JSON mapper provider.
 */
@Weight(Weighted.DEFAULT_WEIGHT)
public class JsonMapperProvider implements DbMapperProvider {
    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<DbMapper<T>> mapper(Class<T> type) {
        if (type.equals(JsonObject.class)) {
            return Optional.of((DbMapper<T>) JsonMapper.create());
        }
        return Optional.empty();
    }
}
