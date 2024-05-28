/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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
package io.helidon.dbclient.tests.common.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbRow;

import static io.helidon.dbclient.tests.common.model.Kind.KINDS;

/**
 * {@code Critter} POJO.
 */
@SuppressWarnings("SpellCheckingInspection")
public class Critter {

    /**
     * {@code Critter} POJO mapper.
     */
    public static final class CritterMapper implements DbMapper<Critter> {

        public static final CritterMapper INSTANCE = new CritterMapper();

        @Override
        public Critter read(DbRow row) {
            return new Critter(row.column("id").get(Integer.class), row.column("name").get(String.class));
        }

        @Override
        public Map<String, ?> toNamedParameters(Critter value) {
            Map<String, Object> params = new HashMap<>(2);
            params.put("id", value.getId());
            params.put("name", value.getName());
            return params;
        }

        @Override
        public List<?> toIndexedParameters(Critter value) {
            List<Object> params = new ArrayList<>(2);
            params.add(value.getName());
            params.add(value.getId());
            return params;
        }

    }

    /**
     * Map of {@code Critter} by ID.
     */
    public static final Map<Integer, Critter> CRITTERS = new HashMap<>();

    static {
        // query tests
        CRITTERS.put(1, new Critter(1, "Pikachu", KINDS.get(13)));
        CRITTERS.put(2, new Critter(2, "Raichu", KINDS.get(13)));
        CRITTERS.put(3, new Critter(3, "Machop", KINDS.get(2)));
        CRITTERS.put(4, new Critter(4, "Snorlax", KINDS.get(1)));
        CRITTERS.put(5, new Critter(5, "Charizard", KINDS.get(10), KINDS.get(3)));
        CRITTERS.put(6, new Critter(6, "Meowth", KINDS.get(1)));
        CRITTERS.put(7, new Critter(7, "Gyarados", KINDS.get(3), KINDS.get(11)));
        // update tests
        CRITTERS.put(8, new Critter(8, "Spearow", KINDS.get(1), KINDS.get(3)));
        CRITTERS.put(9, new Critter(9, "Fearow", KINDS.get(1), KINDS.get(3)));
        CRITTERS.put(10, new Critter(10, "Ekans", KINDS.get(4)));
        CRITTERS.put(11, new Critter(11, "Arbok", KINDS.get(4)));
        CRITTERS.put(12, new Critter(12, "Sandshrew", KINDS.get(5)));
        CRITTERS.put(13, new Critter(13, "Sandslash", KINDS.get(5)));
        CRITTERS.put(14, new Critter(14, "Diglett", KINDS.get(5)));
        // delete tests
        CRITTERS.put(15, new Critter(15, "Rayquaza", KINDS.get(3), KINDS.get(16)));
        CRITTERS.put(16, new Critter(16, "Lugia", KINDS.get(3), KINDS.get(14)));
        CRITTERS.put(17, new Critter(17, "Ho-Oh", KINDS.get(3), KINDS.get(10)));
        CRITTERS.put(18, new Critter(18, "Raikou", KINDS.get(13)));
        CRITTERS.put(19, new Critter(19, "Giratina", KINDS.get(8), KINDS.get(16)));
        CRITTERS.put(20, new Critter(20, "Regirock", KINDS.get(6)));
        CRITTERS.put(21, new Critter(21, "Kyogre", KINDS.get(11)));
        // IDs reserved for insert tests are 22 - 28
        //  DML update tests
        CRITTERS.put(29, new Critter(29, "Piplup", KINDS.get(11)));
        CRITTERS.put(30, new Critter(30, "Prinplup", KINDS.get(11)));
        CRITTERS.put(31, new Critter(31, "Empoleon", KINDS.get(9), KINDS.get(11)));
        CRITTERS.put(32, new Critter(32, "Staryu", KINDS.get(11)));
        CRITTERS.put(33, new Critter(33, "Starmie", KINDS.get(11), KINDS.get(14)));
        CRITTERS.put(34, new Critter(34, "Horsea", KINDS.get(11)));
        CRITTERS.put(35, new Critter(35, "Seadra", KINDS.get(11)));
        // DML delete tests
        CRITTERS.put(36, new Critter(36, "Mudkip", KINDS.get(11)));
        CRITTERS.put(37, new Critter(37, "Marshtomp", KINDS.get(5), KINDS.get(11)));
        CRITTERS.put(38, new Critter(38, "Swampert", KINDS.get(5), KINDS.get(11)));
        CRITTERS.put(39, new Critter(39, "Muk", KINDS.get(4)));
        CRITTERS.put(40, new Critter(40, "Grimer", KINDS.get(4)));
        CRITTERS.put(41, new Critter(41, "Cubchoo", KINDS.get(15)));
        CRITTERS.put(42, new Critter(42, "Beartic", KINDS.get(15)));
        // IDs reserved for DML insert tests are 43 - 49
        // DML statement IT
        CRITTERS.put(50, new Critter(50, "Shinx", KINDS.get(13)));
        CRITTERS.put(51, new Critter(51, "Luxio", KINDS.get(13)));
        CRITTERS.put(52, new Critter(52, "Luxray", KINDS.get(13)));
        CRITTERS.put(53, new Critter(53, "Kricketot", KINDS.get(7)));
        CRITTERS.put(54, new Critter(54, "Kricketune", KINDS.get(7)));
        CRITTERS.put(55, new Critter(55, "Phione", KINDS.get(11)));
        CRITTERS.put(56, new Critter(56, "Chatot", KINDS.get(1), KINDS.get(3)));
        // update tests in transaction
        CRITTERS.put(57, new Critter(57, "Teddiursa", KINDS.get(1)));
        CRITTERS.put(58, new Critter(58, "Ursaring", KINDS.get(1)));
        CRITTERS.put(59, new Critter(59, "Slugma", KINDS.get(10)));
        CRITTERS.put(60, new Critter(60, "Magcargo", KINDS.get(6), KINDS.get(10)));
        CRITTERS.put(61, new Critter(61, "Lotad", KINDS.get(11), KINDS.get(12)));
        CRITTERS.put(62, new Critter(62, "Lombre", KINDS.get(11), KINDS.get(12)));
        CRITTERS.put(63, new Critter(63, "Ludicolo", KINDS.get(11), KINDS.get(12)));
        // DML update tests in transaction
        CRITTERS.put(64, new Critter(64, "Natu", KINDS.get(3), KINDS.get(14)));
        CRITTERS.put(65, new Critter(65, "Xatu", KINDS.get(3), KINDS.get(14)));
        CRITTERS.put(66, new Critter(66, "Snubbull", KINDS.get(18)));
        CRITTERS.put(67, new Critter(67, "Granbull", KINDS.get(18)));
        CRITTERS.put(68, new Critter(68, "Entei", KINDS.get(10)));
        CRITTERS.put(69, new Critter(69, "Raikou", KINDS.get(13)));
        CRITTERS.put(70, new Critter(70, "Suicune", KINDS.get(11)));
        // delete tests in transaction
        CRITTERS.put(71, new Critter(71, "Omanyte", KINDS.get(6), KINDS.get(11)));
        CRITTERS.put(72, new Critter(72, "Omastar", KINDS.get(6), KINDS.get(11)));
        CRITTERS.put(73, new Critter(73, "Kabuto", KINDS.get(6), KINDS.get(11)));
        CRITTERS.put(74, new Critter(74, "Kabutops", KINDS.get(6), KINDS.get(11)));
        CRITTERS.put(75, new Critter(75, "Chikorita", KINDS.get(12)));
        CRITTERS.put(76, new Critter(76, "Bayleef", KINDS.get(12)));
        CRITTERS.put(77, new Critter(77, "Meganium", KINDS.get(12)));
        // DML delete tests in transaction
        CRITTERS.put(78, new Critter(78, "Trapinch", KINDS.get(5)));
        CRITTERS.put(79, new Critter(79, "Vibrava", KINDS.get(5), KINDS.get(16)));
        CRITTERS.put(80, new Critter(80, "Spoink", KINDS.get(14)));
        CRITTERS.put(81, new Critter(81, "Grumpig", KINDS.get(14)));
        CRITTERS.put(82, new Critter(82, "Beldum", KINDS.get(9), KINDS.get(14)));
        CRITTERS.put(83, new Critter(83, "Metang", KINDS.get(9), KINDS.get(14)));
        CRITTERS.put(84, new Critter(84, "Metagross", KINDS.get(9), KINDS.get(14)));
        // IDs reserved for DML insert tests in transaction are 85 - 98
        // mapping tests
        CRITTERS.put(99, new Critter(99, "Moltres", KINDS.get(3), KINDS.get(10)));
        CRITTERS.put(100, new Critter(100, "Masquerain", KINDS.get(3), KINDS.get(7)));
        CRITTERS.put(101, new Critter(101, "Makuhita", KINDS.get(2)));
        CRITTERS.put(102, new Critter(102, "Hariyama", KINDS.get(2)));
        // IDs reserved for mapping tests with insert are 103 - 104
    }

    public static List<Kind> typesList(Kind... types) {
        if (types == null) {
            return null;
        }
        List<Kind> typesList = new ArrayList<>(types.length);
        typesList.addAll(Arrays.asList(types));
        return typesList;
    }

    private final int id;
    private final String name;
    private final List<Kind> types;

    public Critter(int id, String name, Kind... types) {
        this.id = id;
        this.name = name;
        this.types = new ArrayList<>(types != null ? types.length : 0);
        if (types != null) {
            this.types.addAll(Arrays.asList(types));
        }
    }

    public Critter(int id, String name, List<Kind> types) {
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

    public List<Kind> getTypes() {
        return types;
    }

    public Kind[] getTypesArray() {
        return types.toArray(new Kind[0]);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Critter: {id=");
        sb.append(id);
        sb.append(", name=");
        sb.append(name);
        sb.append(", types=[");
        boolean first = true;
        for (Kind type : types) {
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

}
