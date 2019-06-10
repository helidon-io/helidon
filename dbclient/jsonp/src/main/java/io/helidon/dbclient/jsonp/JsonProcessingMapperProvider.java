/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient.jsonp;

import java.util.Optional;

import javax.annotation.Priority;
import javax.json.JsonObject;

import io.helidon.common.Prioritized;
import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.spi.DbMapperProvider;

/**
 * JSON-P mapper provider.
 */
@Priority(Prioritized.DEFAULT_PRIORITY)
public class JsonProcessingMapperProvider implements DbMapperProvider {
    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<DbMapper<T>> mapper(Class<T> type) {
        if (type.equals(JsonObject.class)) {
            return Optional.of((DbMapper<T>) JsonProcessingMapper.create());
        }
        return Optional.empty();
    }
}
