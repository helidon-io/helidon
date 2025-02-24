/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.common.model;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

/**
 * {@code Pokemon} data set.
 */
@ApplicationScoped
@SuppressWarnings("SpellCheckingInspection")
public class PokemonDataSet {

    private final Type normal = new Type(1, "Normal");
    private final Type fighting = new Type(2, "Fighting");
    private final Type flying = new Type(3, "Flying");
    private final Type poison = new Type(4, "Poison");
    private final Type ground = new Type(5, "Ground");
    private final Type rock = new Type(6, "Rock");
    private final Type bug = new Type(7, "Bug");
    private final Type ghost = new Type(8, "Ghost");
    private final Type steel = new Type(9, "Steel");
    private final Type fire = new Type(10, "Fire");
    private final Type water = new Type(11, "Water");
    private final Type grass = new Type(12, "Grass");
    private final Type electric = new Type(13, "Electric");
    private final Type psychic = new Type(14, "Psychic");
    private final Type ice = new Type(15, "Ice");
    private final Type dragon = new Type(16, "Dragon");
    private final Type dark = new Type(17, "Dark");
    private final Type fairy = new Type(18, "Fairy");

    private final Trainer ash = new Trainer("Ash Ketchum", 10);
    private final Trainer misty = new Trainer("Misty", 10);
    private final Trainer brock = new Trainer("Brock", 12);
    private final Trainer giovanni = new Trainer("Giovanni", 37);
    private final Trainer erika = new Trainer("Erika", 16);
    private final Trainer sabrina = new Trainer("Sabrina", 23);

    private final Pokemon pikachu = new Pokemon(ash, "Pikachu", 252, List.of(electric));
    private final Pokemon caterpie = new Pokemon(ash, "Caterpie", 123, List.of(bug));
    private final Pokemon charmander = new Pokemon(ash, "Charmander", 207, List.of(fire));
    private final Pokemon squirtle = new Pokemon(ash, "Squirtle", 187, List.of(water));
    private final Pokemon bulbasaur = new Pokemon(ash, "Bulbasaur", 204, List.of(grass, poison));
    private final Pokemon pidgey = new Pokemon(ash, "Pidgey", 107, List.of(normal, flying));

    private final Pokemon staryu = new Pokemon(misty, "Staryu", 184, List.of(water));
    private final Pokemon psyduck = new Pokemon(misty, "Psyduck", 92, List.of(water));
    private final Pokemon corsola = new Pokemon(misty, "Corsola", 147, List.of(rock));
    private final Pokemon horsea = new Pokemon(misty, "Horsea", 64, List.of(water));
    private final Pokemon azurill = new Pokemon(misty, "Azurill", 217, List.of(normal, fairy));
    private final Pokemon togepi = new Pokemon(misty, "Togepi", 51, List.of(fairy));

    private final Pokemon geodude = new Pokemon(brock, "Geodude", 236, List.of(rock, ground));
    private final Pokemon onix = new Pokemon(brock, "Onix", 251, List.of(rock, ground));
    private final Pokemon rhyhorn = new Pokemon(brock, "Rhyhorn", 251, List.of(rock, ground));
    private final Pokemon slowpoke = new Pokemon(brock, "Slowpoke", 251, List.of(water, psychic));
    private final Pokemon teddiursa = new Pokemon(brock, "Teddiursa", 275, List.of(normal));
    private final Pokemon omanyte = new Pokemon(brock, "Omanyte", 275, List.of(rock, water));

    private final Pokemon rhyperior = new Pokemon(giovanni, "Rhyperior", 3841, List.of(ground, rock));
    private final Pokemon golem = new Pokemon(giovanni, "Golem", 3651, List.of(ground, rock));
    private final Pokemon nidoking = new Pokemon(giovanni, "Nidoking", 2451, List.of(ground, poison));
    private final Pokemon marowak = new Pokemon(giovanni, "Marowak", 2249, List.of(ground));
    private final Pokemon sandslash = new Pokemon(giovanni, "Sandslash", 1953, List.of(ground));
    private final Pokemon nidoqueen = new Pokemon(giovanni, "Nidoqueen", 3147, List.of(ground));

    private final Pokemon alakazam = new Pokemon(sabrina, "Alakazam", 2178, List.of(psychic));
    private final Pokemon espeon = new Pokemon(sabrina, "Espeon", 2745, List.of(psychic));
    private final Pokemon pokemon = new Pokemon(sabrina, "Mr. Mime", 1478, List.of(psychic));
    private final Pokemon jynx = new Pokemon(sabrina, "Jynx", 2471, List.of(psychic, ice));
    private final Pokemon wobbuffet = new Pokemon(sabrina, "Wobbuffet", 1478, List.of(psychic));
    private final Pokemon gallade = new Pokemon(sabrina, "Gallade", 2147, List.of(psychic, fighting));

    private final Pokemon gloom = new Pokemon(erika, "Gloom", 651, List.of(grass, poison));
    private final Pokemon victreebel = new Pokemon(erika, "Victreebel", 751, List.of(grass, poison));
    private final Pokemon tangela = new Pokemon(erika, "Tangela", 234, List.of(grass));
    private final Pokemon vileplume = new Pokemon(erika, "Vileplume", 1571, List.of(grass, poison));
    private final Pokemon weepinbell = new Pokemon(erika, "Weepinbell", 1923, List.of(grass, poison));
    private final Pokemon exeggcute = new Pokemon(erika, "Exeggcute", 317, List.of(grass, psychic));

    private final Stadium viridianGym = new Stadium("Viridian Gym", giovanni);
    private final Stadium celadonGym = new Stadium("Celadon Gym", erika);
    private final Stadium saffronGym = new Stadium("Saffron Gym", sabrina);

    private final City viridian = new City("Viridian City", "Koichi", viridianGym);
    private final City celadon = new City("Celadon City", "Madam Celadon", celadonGym);
    private final City saffron = new City("Saffron City", "Koichi", saffronGym);

    private final List<Type> allTypes = List.of(
            normal, fighting, flying, poison, ground, rock, bug, ghost, steel, fire,
            water, grass, electric, psychic, ice, dragon, dark, fairy);

    private final List<Pokemon> allPokemons = List.of(
            pikachu, caterpie, charmander, squirtle, bulbasaur, pidgey,
            staryu, psyduck, corsola, horsea, azurill, togepi, geodude, onix, rhyhorn, slowpoke, teddiursa, omanyte, rhyperior,
            golem, nidoking, marowak, sandslash, nidoqueen, alakazam, espeon, pokemon, jynx, wobbuffet, gallade, gloom,
            victreebel, tangela, vileplume, weepinbell, exeggcute);

    private final List<Trainer> allTrainers = List.of(ash, misty, brock, giovanni, erika, sabrina);
    private final List<Stadium> allStadiums = List.of(viridianGym, celadonGym, saffronGym);
    private final List<City> allCities = List.of(viridian, celadon, saffron);

    @PersistenceContext(unitName = "test")
    private EntityManager em;

    @Transactional
    private void setup(@Observes @Initialized(ApplicationScoped.class) Object event) {
        allTypes.forEach(em::persist);
        allTrainers.forEach(em::persist);
        allPokemons.forEach(em::persist);
        allStadiums.forEach(em::persist);
        allCities.forEach(em::persist);
        em.flush();
        em.clear();
        em.getEntityManagerFactory().getCache().evictAll();
    }

    /**
     * Get Ash.
     *
     * @return Trainer
     */
    public Trainer ash() {
        return ash;
    }

    /**
     * Get the water type.
     *
     * @return Type
     */
    public Type water() {
        return water;
    }

    /**
     * Get the poison type.
     *
     * @return Type
     */
    public Type poison() {
        return poison;
    }

    /**
     * Get the normal type.
     *
     * @return Type
     */
    public Type normal() {
        return normal;
    }

    /**
     * Get the electric type.
     *
     * @return Type
     */
    public Type electric() {
        return electric;
    }

    /**
     * Get the flying type.
     *
     * @return Type
     */
    public Type flying() {
        return flying;
    }

    /**
     * Get the fire type.
     *
     * @return Type
     */
    public Type fire() {
        return fire;
    }
}
