/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.appl.model;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbRow;

import static io.helidon.tests.integration.dbclient.appl.model.Type.TYPES;

/**
 * Pokemon POJO.
 */
public class Pokemon {

    /**
     * Pokemon POJO mapper.
     */
    public static final class PokemonMapper implements DbMapper<Pokemon> {

        public static final PokemonMapper INSTANCE = new PokemonMapper();

        @Override
        public Pokemon read(DbRow row) {
            return new Pokemon(row.column("id").as(Integer.class), row.column("name").as(String.class));
        }

        @Override
        public Map<String, ?> toNamedParameters(Pokemon value) {
            Map<String, Object> params = new HashMap<>(2);
            params.put("id", value.getId());
            params.put("name", value.getName());
            return params;
        }

        @Override
        public List<?> toIndexedParameters(Pokemon value) {
            List<Object> params = new ArrayList<>(2);
            params.add(value.getName());
            params.add(value.getId());
            return params;
        }

    }

    /** Map of pokemons. */
    public static final Map<Integer, Pokemon> POKEMONS = new HashMap<>();

    // Initialize pokemons Map
    static {
        // Pokemons for query tests
        POKEMONS.put( 1, new Pokemon( 1, "Pikachu", TYPES.get(13)));
        POKEMONS.put( 2, new Pokemon( 2, "Raichu", TYPES.get(13)));
        POKEMONS.put( 3, new Pokemon( 3, "Machop", TYPES.get(2)));
        POKEMONS.put( 4, new Pokemon( 4, "Snorlax", TYPES.get(1)));
        POKEMONS.put( 5, new Pokemon( 5, "Charizard", TYPES.get(10), TYPES.get(3)));
        POKEMONS.put( 6, new Pokemon( 6, "Meowth", TYPES.get(1)));
        POKEMONS.put( 7, new Pokemon( 7, "Gyarados", TYPES.get(3), TYPES.get(11)));
        // Pokemons for update tests
        POKEMONS.put( 8, new Pokemon( 8, "Spearow", TYPES.get(1), TYPES.get(3)));
        POKEMONS.put( 9, new Pokemon( 9, "Fearow", TYPES.get(1), TYPES.get(3)));
        POKEMONS.put(10, new Pokemon(10, "Ekans", TYPES.get(4)));
        POKEMONS.put(11, new Pokemon(11, "Arbok", TYPES.get(4)));
        POKEMONS.put(12, new Pokemon(12, "Sandshrew", TYPES.get(5)));
        POKEMONS.put(13, new Pokemon(13, "Sandslash", TYPES.get(5)));
        POKEMONS.put(14, new Pokemon(14, "Diglett", TYPES.get(5)));
        // Pokemons for delete tests
        POKEMONS.put(15, new Pokemon(15, "Rayquaza", TYPES.get(3), TYPES.get(16)));
        POKEMONS.put(16, new Pokemon(16, "Lugia", TYPES.get(3), TYPES.get(14)));
        POKEMONS.put(17, new Pokemon(17, "Ho-Oh", TYPES.get(3), TYPES.get(10)));
        POKEMONS.put(18, new Pokemon(18, "Raikou", TYPES.get(13)));
        POKEMONS.put(19, new Pokemon(19, "Giratina", TYPES.get(8), TYPES.get(16)));
        POKEMONS.put(20, new Pokemon(20, "Regirock", TYPES.get(6)));
        POKEMONS.put(21, new Pokemon(21, "Kyogre", TYPES.get(11)));
        // IDs reserved for insert tests are 22 - 28
        // Pokemons for DML update tests
        POKEMONS.put(29, new Pokemon(29, "Piplup", TYPES.get(11)));
        POKEMONS.put(30, new Pokemon(30, "Prinplup", TYPES.get(11)));
        POKEMONS.put(31, new Pokemon(31, "Empoleon", TYPES.get(9), TYPES.get(11)));
        POKEMONS.put(32, new Pokemon(32, "Staryu", TYPES.get(11)));
        POKEMONS.put(33, new Pokemon(33, "Starmie", TYPES.get(11), TYPES.get(14)));
        POKEMONS.put(34, new Pokemon(34, "Horsea", TYPES.get(11)));
        POKEMONS.put(35, new Pokemon(35, "Seadra", TYPES.get(11)));
        // Pokemons for DML delete tests
        POKEMONS.put(36, new Pokemon(36, "Mudkip", TYPES.get(11)));
        POKEMONS.put(37, new Pokemon(37, "Marshtomp", TYPES.get(5), TYPES.get(11)));
        POKEMONS.put(38, new Pokemon(38, "Swampert", TYPES.get(5), TYPES.get(11)));
        POKEMONS.put(39, new Pokemon(39, "Muk", TYPES.get(4)));
        POKEMONS.put(40, new Pokemon(40, "Grimer", TYPES.get(4)));
        POKEMONS.put(41, new Pokemon(41, "Cubchoo", TYPES.get(15)));
        POKEMONS.put(42, new Pokemon(42, "Beartic", TYPES.get(15)));
        // IDs reserved for DML insert tests are 43 - 49
        // DML statement IT
        POKEMONS.put(50, new Pokemon(50, "Shinx", TYPES.get(13)));
        POKEMONS.put(51, new Pokemon(51, "Luxio", TYPES.get(13)));
        POKEMONS.put(52, new Pokemon(52, "Luxray", TYPES.get(13)));
        POKEMONS.put(53, new Pokemon(53, "Kricketot", TYPES.get(7)));
        POKEMONS.put(54, new Pokemon(54, "Kricketune", TYPES.get(7)));
        POKEMONS.put(55, new Pokemon(55, "Phione", TYPES.get(11)));
        POKEMONS.put(56, new Pokemon(56, "Chatot", TYPES.get(1), TYPES.get(3)));
        // Pokemons for update tests in transaction
        POKEMONS.put(57, new Pokemon(57, "Teddiursa", TYPES.get(1)));
        POKEMONS.put(58, new Pokemon(58, "Ursaring", TYPES.get(1)));
        POKEMONS.put(59, new Pokemon(59, "Slugma", TYPES.get(10)));
        POKEMONS.put(60, new Pokemon(60, "Magcargo", TYPES.get(6), TYPES.get(10)));
        POKEMONS.put(61, new Pokemon(61, "Lotad", TYPES.get(11), TYPES.get(12)));
        POKEMONS.put(62, new Pokemon(62, "Lombre", TYPES.get(11), TYPES.get(12)));
        POKEMONS.put(63, new Pokemon(63, "Ludicolo", TYPES.get(11), TYPES.get(12)));
        // Pokemons for DML update tests in transaction
        POKEMONS.put(64, new Pokemon(64, "Natu", TYPES.get(3), TYPES.get(14)));
        POKEMONS.put(65, new Pokemon(65, "Xatu", TYPES.get(3), TYPES.get(14)));
        POKEMONS.put(66, new Pokemon(66, "Snubbull", TYPES.get(18)));
        POKEMONS.put(67, new Pokemon(67, "Granbull", TYPES.get(18)));
        POKEMONS.put(68, new Pokemon(68, "Entei", TYPES.get(10)));
        POKEMONS.put(69, new Pokemon(69, "Raikou", TYPES.get(13)));
        POKEMONS.put(70, new Pokemon(70, "Suicune", TYPES.get(11)));
        // Pokemons for delete tests in transaction
        POKEMONS.put(71, new Pokemon(71, "Omanyte", TYPES.get(6), TYPES.get(11)));
        POKEMONS.put(72, new Pokemon(72, "Omastar", TYPES.get(6), TYPES.get(11)));
        POKEMONS.put(73, new Pokemon(73, "Kabuto", TYPES.get(6), TYPES.get(11)));
        POKEMONS.put(74, new Pokemon(74, "Kabutops", TYPES.get(6), TYPES.get(11)));
        POKEMONS.put(75, new Pokemon(75, "Chikorita", TYPES.get(12)));
        POKEMONS.put(76, new Pokemon(76, "Bayleef", TYPES.get(12)));
        POKEMONS.put(77, new Pokemon(77, "Meganium", TYPES.get(12)));
        // Pokemons for DML delete tests in transaction
        POKEMONS.put(78, new Pokemon(78, "Trapinch", TYPES.get(5)));
        POKEMONS.put(79, new Pokemon(79, "Vibrava", TYPES.get(5), TYPES.get(16)));
        POKEMONS.put(80, new Pokemon(80, "Spoink", TYPES.get(14)));
        POKEMONS.put(81, new Pokemon(81, "Grumpig", TYPES.get(14)));
        POKEMONS.put(82, new Pokemon(82, "Beldum", TYPES.get(9), TYPES.get(14)));
        POKEMONS.put(83, new Pokemon(83, "Metang", TYPES.get(9), TYPES.get(14)));
        POKEMONS.put(84, new Pokemon(84, "Metagross", TYPES.get(9), TYPES.get(14)));
        // IDs reserved for DML insert tests in transaction are 85 - 98
        // Pokemons for mapping tests
        POKEMONS.put( 99, new Pokemon( 99, "Moltres", TYPES.get(3), TYPES.get(10)));
        POKEMONS.put(100, new Pokemon(100, "Masquerain", TYPES.get(3), TYPES.get(7)));
        POKEMONS.put(101, new Pokemon(101, "Makuhita", TYPES.get(2)));
        POKEMONS.put(102, new Pokemon(102, "Hariyama", TYPES.get(2)));
        // IDs reserved for mapping tests with insert are 103 - 104
    }

    public static final List<Type> typesList(Type... types) {
        if (types == null) {
            return null;
        }
        final List<Type> typesList = new ArrayList<>(types.length);
        typesList.addAll(Arrays.asList(types));
        return typesList;
    }

    private final int id;
    private final String name;
    private final List<Type> types;

    public Pokemon(int id, String name, Type... types) {
        this.id = id;
        this.name = name;
        this.types = new ArrayList<>(types != null ? types.length : 0);
        if (types != null) {
            this.types.addAll(Arrays.asList(types));
        }
    }

    public Pokemon(int id, String name, List<Type> types) {
        this.id = id;
        this.name = name;
        this.types = types;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Type> getTypes() {
        return types;
    }

    public Type[] getTypesArray() {
        return types.toArray(new Type[types.size()]);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Pokemon: {id=");
        sb.append(id);
        sb.append(", name=");
        sb.append(name);
        sb.append(", types=[");
        boolean first = true;
        for (Type type : types) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(type.toString());
        }
        sb.append("]}");
        return sb.toString();
    }

    public JsonObject toJsonObject() {
        final JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("id", id);
        job.add("name", name);
        final JsonArrayBuilder typesArray = Json.createArrayBuilder();
        types.forEach(type -> {
            typesArray.add(type.toJsonObject());
        });
        job.add("types", typesArray.build());
        return job.build();
    }

}
