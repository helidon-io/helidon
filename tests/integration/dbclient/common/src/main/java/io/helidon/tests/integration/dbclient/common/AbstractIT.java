/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbRow;

/**
 * Common testing code.
 */
public abstract class AbstractIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(AbstractIT.class.getName());

    public static final Config CONFIG = Config.create(ConfigSources.classpath(ConfigIT.configFile()));

    public static final DbClient DB_CLIENT = initDbClient();

    /**
     * Initialize database client.
     *
     * @return database client instance
     */
    public static DbClient initDbClient() {
        Config dbConfig = CONFIG.get("db");
        return DbClient.builder(dbConfig).build();
    }

    /**
     * Pokemon type POJO.
     */
    public static final class Type {
        private final int id;
        private final String name;

        public Type(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "Type: {id="+id+", name="+name+"}";
        }

    }

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

    /**
     * Pokemon POJO.
     */
    public static final class Pokemon {

        private final int id;
        private final String name;
        private final List<Type> types;

        public Pokemon(int id, String name, Type... types) {
            this.id = id;
            this.name = name;
            this.types = new ArrayList<>(types != null ? types.length : 0);
            if (types != null) {
                for (Type type : types) {
                    this.types.add(type);
                }
            }
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

    }

    /** Map of pokemon types. */
    public static final Map<Integer, Type> TYPES = new HashMap<>();

    // Initialize pokemon types Map
    static {
        TYPES.put(1, new Type(1, "Normal"));
        TYPES.put(2, new Type(2, "Fighting"));
        TYPES.put(3, new Type(3, "Flying"));
        TYPES.put(4, new Type(4, "Poison"));
        TYPES.put(5, new Type(5, "Ground"));
        TYPES.put(6, new Type(6, "Rock"));
        TYPES.put(7, new Type(7, "Bug"));
        TYPES.put(8, new Type(8, "Ghost"));
        TYPES.put(9, new Type(9, "Steel"));
        TYPES.put(10, new Type(10, "Fire"));
        TYPES.put(11, new Type(11, "Water"));
        TYPES.put(12, new Type(12, "Grass"));
        TYPES.put(13, new Type(13, "Electric"));
        TYPES.put(14, new Type(14, "Psychic"));
        TYPES.put(15, new Type(15, "Ice"));
        TYPES.put(16, new Type(16, "Dragon"));
        TYPES.put(17, new Type(17, "Dark"));
        TYPES.put(18, new Type(18, "Fairy"));
    }

    /** Map of pokemons. */
    public static final Map<Integer, Pokemon> POKEMONS = new HashMap<>();

    // Initialize pokemons Map
    static {
        // Pokemons for query tests
        POKEMONS.put(1, new Pokemon(1, "Pikachu", TYPES.get(13)));
        POKEMONS.put(2, new Pokemon(2, "Raichu", TYPES.get(13)));
        POKEMONS.put(3, new Pokemon(3, "Machop", TYPES.get(2)));
        POKEMONS.put(4, new Pokemon(4, "Snorlax", TYPES.get(1)));
        POKEMONS.put(5, new Pokemon(5, "Charizard", TYPES.get(10), TYPES.get(3)));
        POKEMONS.put(6, new Pokemon(6, "Meowth", TYPES.get(1)));
        POKEMONS.put(7, new Pokemon(7, "Gyarados", TYPES.get(3), TYPES.get(11)));
    }

    /** Last used id in Pokemons table. */
    public static final int LAST_POKEMON_ID = 5;

    /** Select statement with named arguments for Pokemon class. */
    public static final String SELECT_POKEMON_NAMED_ARG
            = CONFIG.get("db.statements.select-pokemon-named-arg").asString().get();

    /** Select statement with ordered arguments for Pokemon class. */
    public static final String SELECT_POKEMON_ORDER_ARG
            = CONFIG.get("db.statements.select-pokemon-order-arg").asString().get();

    /** Insert statement with named arguments for Pokemon class. */
    public static final String INSERT_POKEMON_NAMED_ARG
            = CONFIG.get("db.statements.insert-pokemon-named-arg").asString().get();

    /** Insert statement with ordered arguments for Pokemon class. */
    public static final String INSERT_POKEMON_ORDER_ARG
            = CONFIG.get("db.statements.insert-pokemon-order-arg").asString().get();

    /** Update statement with named arguments for Pokemon class. */
    public static final String UPDATE_POKEMON_NAMED_ARG
            = CONFIG.get("db.statements.update-pokemon-named-arg").asString().get();

    /** Update statement with ordered arguments for Pokemon class. */
    public static final String UPDATE_POKEMON_ORDER_ARG
            = CONFIG.get("db.statements.update-pokemon-order-arg").asString().get();

    /** Delete statement with named arguments for Pokemon class. */
    public static final String DELETE_POKEMON_NAMED_ARG
            = CONFIG.get("db.statements.delete-pokemon-named-arg").asString().get();

    /** Delete statement with ordered arguments for Pokemon class. */
    public static final String DELETE_POKEMON_ORDER_ARG
            = CONFIG.get("db.statements.delete-pokemon-order-arg").asString().get();

}
