/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbRow;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * {@code Pokemon} POJO.
 */
@SuppressWarnings("SpellCheckingInspection")
public class Pokemon {

    /**
     * {@code Pokemon} POJO mapper.
     */
    public static final class PokemonMapper implements DbMapper<Pokemon> {

        public static final PokemonMapper INSTANCE = new PokemonMapper();

        @Override
        public Pokemon read(DbRow row) {
            return new Pokemon(row.column("id").get(Integer.class), row.column("name").get(String.class));
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

    /**
     * Map of {@code Pokemon} by ID.
     */
    public static final Map<Integer, Pokemon> POKEMONS = new HashMap<>();

    static {
        // query tests
        POKEMONS.put(1, new Pokemon(1, "Pikachu", Type.TYPES.get(13)));
        POKEMONS.put(2, new Pokemon(2, "Raichu", Type.TYPES.get(13)));
        POKEMONS.put(3, new Pokemon(3, "Machop", Type.TYPES.get(2)));
        POKEMONS.put(4, new Pokemon(4, "Snorlax", Type.TYPES.get(1)));
        POKEMONS.put(5, new Pokemon(5, "Charizard", Type.TYPES.get(10), Type.TYPES.get(3)));
        POKEMONS.put(6, new Pokemon(6, "Meowth", Type.TYPES.get(1)));
        POKEMONS.put(7, new Pokemon(7, "Gyarados", Type.TYPES.get(3), Type.TYPES.get(11)));
        // update tests
        POKEMONS.put(8, new Pokemon(8, "Spearow", Type.TYPES.get(1), Type.TYPES.get(3)));
        POKEMONS.put(9, new Pokemon(9, "Fearow", Type.TYPES.get(1), Type.TYPES.get(3)));
        POKEMONS.put(10, new Pokemon(10, "Ekans", Type.TYPES.get(4)));
        POKEMONS.put(11, new Pokemon(11, "Arbok", Type.TYPES.get(4)));
        POKEMONS.put(12, new Pokemon(12, "Sandshrew", Type.TYPES.get(5)));
        POKEMONS.put(13, new Pokemon(13, "Sandslash", Type.TYPES.get(5)));
        POKEMONS.put(14, new Pokemon(14, "Diglett", Type.TYPES.get(5)));
        // delete tests
        POKEMONS.put(15, new Pokemon(15, "Rayquaza", Type.TYPES.get(3), Type.TYPES.get(16)));
        POKEMONS.put(16, new Pokemon(16, "Lugia", Type.TYPES.get(3), Type.TYPES.get(14)));
        POKEMONS.put(17, new Pokemon(17, "Ho-Oh", Type.TYPES.get(3), Type.TYPES.get(10)));
        POKEMONS.put(18, new Pokemon(18, "Raikou", Type.TYPES.get(13)));
        POKEMONS.put(19, new Pokemon(19, "Giratina", Type.TYPES.get(8), Type.TYPES.get(16)));
        POKEMONS.put(20, new Pokemon(20, "Regirock", Type.TYPES.get(6)));
        POKEMONS.put(21, new Pokemon(21, "Kyogre", Type.TYPES.get(11)));
        // IDs reserved for insert tests are 22 - 28
        //  DML update tests
        POKEMONS.put(29, new Pokemon(29, "Piplup", Type.TYPES.get(11)));
        POKEMONS.put(30, new Pokemon(30, "Prinplup", Type.TYPES.get(11)));
        POKEMONS.put(31, new Pokemon(31, "Empoleon", Type.TYPES.get(9), Type.TYPES.get(11)));
        POKEMONS.put(32, new Pokemon(32, "Staryu", Type.TYPES.get(11)));
        POKEMONS.put(33, new Pokemon(33, "Starmie", Type.TYPES.get(11), Type.TYPES.get(14)));
        POKEMONS.put(34, new Pokemon(34, "Horsea", Type.TYPES.get(11)));
        POKEMONS.put(35, new Pokemon(35, "Seadra", Type.TYPES.get(11)));
        // DML delete tests
        POKEMONS.put(36, new Pokemon(36, "Mudkip", Type.TYPES.get(11)));
        POKEMONS.put(37, new Pokemon(37, "Marshtomp", Type.TYPES.get(5), Type.TYPES.get(11)));
        POKEMONS.put(38, new Pokemon(38, "Swampert", Type.TYPES.get(5), Type.TYPES.get(11)));
        POKEMONS.put(39, new Pokemon(39, "Muk", Type.TYPES.get(4)));
        POKEMONS.put(40, new Pokemon(40, "Grimer", Type.TYPES.get(4)));
        POKEMONS.put(41, new Pokemon(41, "Cubchoo", Type.TYPES.get(15)));
        POKEMONS.put(42, new Pokemon(42, "Beartic", Type.TYPES.get(15)));
        // IDs reserved for DML insert tests are 43 - 49
        // DML statement IT
        POKEMONS.put(50, new Pokemon(50, "Shinx", Type.TYPES.get(13)));
        POKEMONS.put(51, new Pokemon(51, "Luxio", Type.TYPES.get(13)));
        POKEMONS.put(52, new Pokemon(52, "Luxray", Type.TYPES.get(13)));
        POKEMONS.put(53, new Pokemon(53, "Kricketot", Type.TYPES.get(7)));
        POKEMONS.put(54, new Pokemon(54, "Kricketune", Type.TYPES.get(7)));
        POKEMONS.put(55, new Pokemon(55, "Phione", Type.TYPES.get(11)));
        POKEMONS.put(56, new Pokemon(56, "Chatot", Type.TYPES.get(1), Type.TYPES.get(3)));
        // update tests in transaction
        POKEMONS.put(57, new Pokemon(57, "Teddiursa", Type.TYPES.get(1)));
        POKEMONS.put(58, new Pokemon(58, "Ursaring", Type.TYPES.get(1)));
        POKEMONS.put(59, new Pokemon(59, "Slugma", Type.TYPES.get(10)));
        POKEMONS.put(60, new Pokemon(60, "Magcargo", Type.TYPES.get(6), Type.TYPES.get(10)));
        POKEMONS.put(61, new Pokemon(61, "Lotad", Type.TYPES.get(11), Type.TYPES.get(12)));
        POKEMONS.put(62, new Pokemon(62, "Lombre", Type.TYPES.get(11), Type.TYPES.get(12)));
        POKEMONS.put(63, new Pokemon(63, "Ludicolo", Type.TYPES.get(11), Type.TYPES.get(12)));
        // DML update tests in transaction
        POKEMONS.put(64, new Pokemon(64, "Natu", Type.TYPES.get(3), Type.TYPES.get(14)));
        POKEMONS.put(65, new Pokemon(65, "Xatu", Type.TYPES.get(3), Type.TYPES.get(14)));
        POKEMONS.put(66, new Pokemon(66, "Snubbull", Type.TYPES.get(18)));
        POKEMONS.put(67, new Pokemon(67, "Granbull", Type.TYPES.get(18)));
        POKEMONS.put(68, new Pokemon(68, "Entei", Type.TYPES.get(10)));
        POKEMONS.put(69, new Pokemon(69, "Raikou", Type.TYPES.get(13)));
        POKEMONS.put(70, new Pokemon(70, "Suicune", Type.TYPES.get(11)));
        // delete tests in transaction
        POKEMONS.put(71, new Pokemon(71, "Omanyte", Type.TYPES.get(6), Type.TYPES.get(11)));
        POKEMONS.put(72, new Pokemon(72, "Omastar", Type.TYPES.get(6), Type.TYPES.get(11)));
        POKEMONS.put(73, new Pokemon(73, "Kabuto", Type.TYPES.get(6), Type.TYPES.get(11)));
        POKEMONS.put(74, new Pokemon(74, "Kabutops", Type.TYPES.get(6), Type.TYPES.get(11)));
        POKEMONS.put(75, new Pokemon(75, "Chikorita", Type.TYPES.get(12)));
        POKEMONS.put(76, new Pokemon(76, "Bayleef", Type.TYPES.get(12)));
        POKEMONS.put(77, new Pokemon(77, "Meganium", Type.TYPES.get(12)));
        // DML delete tests in transaction
        POKEMONS.put(78, new Pokemon(78, "Trapinch", Type.TYPES.get(5)));
        POKEMONS.put(79, new Pokemon(79, "Vibrava", Type.TYPES.get(5), Type.TYPES.get(16)));
        POKEMONS.put(80, new Pokemon(80, "Spoink", Type.TYPES.get(14)));
        POKEMONS.put(81, new Pokemon(81, "Grumpig", Type.TYPES.get(14)));
        POKEMONS.put(82, new Pokemon(82, "Beldum", Type.TYPES.get(9), Type.TYPES.get(14)));
        POKEMONS.put(83, new Pokemon(83, "Metang", Type.TYPES.get(9), Type.TYPES.get(14)));
        POKEMONS.put(84, new Pokemon(84, "Metagross", Type.TYPES.get(9), Type.TYPES.get(14)));
        // IDs reserved for DML insert tests in transaction are 85 - 98
        // mapping tests
        POKEMONS.put(99, new Pokemon(99, "Moltres", Type.TYPES.get(3), Type.TYPES.get(10)));
        POKEMONS.put(100, new Pokemon(100, "Masquerain", Type.TYPES.get(3), Type.TYPES.get(7)));
        POKEMONS.put(101, new Pokemon(101, "Makuhita", Type.TYPES.get(2)));
        POKEMONS.put(102, new Pokemon(102, "Hariyama", Type.TYPES.get(2)));
        // IDs reserved for mapping tests with insert are 103 - 104
    }

    public static List<Type> typesList(Type... types) {
        if (types == null) {
            return null;
        }
        List<Type> typesList = new ArrayList<>(types.length);
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
        this.types = types != null ? types : List.of();
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
        return types.toArray(new Type[0]);
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
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("id", id);
        job.add("name", name);
        JsonArrayBuilder typesArray = Json.createArrayBuilder();
        types.forEach(type -> typesArray.add(type.toJsonObject()));
        job.add("types", typesArray.build());
        return job.build();
    }

}
