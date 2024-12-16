/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code type} data set.
 */
public final class Types {

    private Types() {
        // cannot be instantiated
    }

    /**
     * Map of {@code Pokemon} types by ID.
     */
    public static final Map<Integer, Type> TYPES = new HashMap<>();

    /**
     * {@code Normal}.
     */
    public static final Type NORMAL = new Type(1, "Normal");

    /**
     * {@code Fighting}.
     */
    public static final Type FIGHTING = new Type(2, "Fighting");

    /**
     * {@code Flying}.
     */
    public static final Type FLYING = new Type(3, "Flying");

    /**
     * {@code Poison}.
     */
    public static final Type POISON = new Type(4, "Poison");

    /**
     * {@code Ground}.
     */
    public static final Type GROUND = new Type(5, "Ground");

    /**
     * {@code Rock}.
     */
    public static final Type ROCK = new Type(6, "Rock");

    /**
     * {@code Bug}.
     */
    public static final Type BUG = new Type(7, "Bug");

    /**
     * {@code Ghost}.
     */
    public static final Type GHOST = new Type(8, "Ghost");

    /**
     * {@code Steel}.
     */
    public static final Type STEEL = new Type(9, "Steel");

    /**
     * {@code Fire}.
     */
    public static final Type FIRE = new Type(10, "Fire");

    /**
     * {@code Water}.
     */
    public static final Type WATER = new Type(11, "Water");

    /**
     * {@code Grass}.
     */
    public static final Type GRASS = new Type(12, "Grass");

    /**
     * {@code Electric}.
     */
    public static final Type ELECTRIC = new Type(13, "Electric");

    /**
     * {@code Psychic}.
     */
    public static final Type PSYCHIC = new Type(14, "Psychic");

    /**
     * {@code Ice}.
     */
    public static final Type ICE = new Type(15, "Ice");

    /**
     * {@code Dragon}.
     */
    public static final Type DRAGON = new Type(16, "Dragon");

    /**
     * {@code Dark}.
     */
    public static final Type DARK = new Type(17, "Dark");

    /**
     * {@code Fairy}.
     */
    public static final Type FAIRY = new Type(18, "Fairy");

    /**
     * All types.
     */
    public static final List<Type> ALL = List.of(NORMAL, FIGHTING, FLYING, POISON, GROUND, ROCK, BUG, GHOST, STEEL, FIRE, WATER,
            GRASS, ELECTRIC, PSYCHIC, ICE, DRAGON, DARK, FAIRY);

    static {
        Types.TYPES.put(1, Types.NORMAL);
        Types.TYPES.put(2, Types.FIGHTING);
        Types.TYPES.put(3, Types.FLYING);
        Types.TYPES.put(4, Types.POISON);
        Types.TYPES.put(5, Types.GROUND);
        Types.TYPES.put(6, Types.ROCK);
        Types.TYPES.put(7, Types.BUG);
        Types.TYPES.put(8, Types.GHOST);
        Types.TYPES.put(9, Types.STEEL);
        Types.TYPES.put(10, Types.FIRE);
        Types.TYPES.put(11, Types.WATER);
        Types.TYPES.put(12, Types.GRASS);
        Types.TYPES.put(13, Types.ELECTRIC);
        Types.TYPES.put(14, Types.PSYCHIC);
        Types.TYPES.put(15, Types.ICE);
        Types.TYPES.put(16, Types.DRAGON);
        Types.TYPES.put(17, Types.DARK);
        Types.TYPES.put(18, Types.FAIRY);
    }
}
