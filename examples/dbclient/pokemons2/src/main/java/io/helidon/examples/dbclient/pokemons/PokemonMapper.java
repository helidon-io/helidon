/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.examples.dbclient.pokemons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbRow;

/**
 * Maps database statements to {@link io.helidon.examples.dbclient.pokemons.Pokemon} class.
 */
public class PokemonMapper implements DbMapper<Pokemon> {

    @Override
    public Pokemon read(DbRow row) {
        DbColumn id = row.column("id");
        DbColumn name = row.column("name");
        DbColumn type = row.column("idType");
        return new Pokemon(id.as(Integer.class), name.as(String.class), type.as(Integer.class));
    }

    @Override
    public Map<String, Object> toNamedParameters(Pokemon value) {
        Map<String, Object> map = new HashMap<>(3);
        map.put("id", value.getId());
        map.put("name", value.getName());
        map.put("idType", value.getIdType());
        return map;
    }

    @Override
    public List<Object> toIndexedParameters(Pokemon value) {
        List<Object> list = new ArrayList<>(3);
        list.add(value.getId());
        list.add(value.getName());
        list.add(value.getIdType());
        return list;
    }
}
