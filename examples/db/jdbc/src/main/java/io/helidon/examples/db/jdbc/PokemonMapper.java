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
package io.helidon.examples.db.jdbc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.db.DbColumn;
import io.helidon.db.DbMapper;
import io.helidon.db.DbRow;

/**
 * Maps database statements to {@link Pokemon} class.
 */
public class PokemonMapper implements DbMapper<Pokemon> {

    @Override
    public Pokemon read(DbRow row) {
        DbColumn name = row.column("name");
        DbColumn type = row.column("type");
        return new Pokemon(name.as(String.class), type.as(String.class));
    }

    @Override
    public Map<String, Object> toNamedParameters(Pokemon value) {
        Map<String, Object> map = new HashMap<>(1);
        map.put("name", value.getName());
        map.put("type", value.getType());
        return map;
    }

    @Override
    public List<Object> toIndexedParameters(Pokemon value) {
        List<Object> list = new ArrayList<>(2);
        list.add(value.getName());
        list.add(value.getType());
        return list;
    }
}
