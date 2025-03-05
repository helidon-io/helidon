/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.tests.codegen.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import io.helidon.data.tests.codegen.model.Pokemon;

import static io.helidon.data.tests.codegen.common.InitialData.POKEMONS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;

class TestUtils {

    // Data.POKEMONS as List
    static final List<Pokemon> POKEMONS_LIST = pokemonsList();

    private TestUtils() {
        throw new UnsupportedOperationException("No instances of TestUtils are allowed");
    }

    static void checkSortedPokemonsListByName(List<Pokemon> pokemons) {
        List<Pokemon> sortedPokemons = new ArrayList<>(POKEMONS_LIST);
        sortedPokemons.sort(TestUtils::comparePokemonsByName);
        assertThat(pokemons, hasSize(sortedPokemons.size()));
        for (int i = 0; i < sortedPokemons.size(); i++) {
            assertThat(pokemons.get(i).getId(), equalTo(sortedPokemons.get(i).getId()));
            assertThat(pokemons.get(i).getName(), equalTo(sortedPokemons.get(i).getName()));
        }
    }

    static void checkSortedPokemonsListByName(Stream<Pokemon> pokemons) {
        List<Pokemon> sortedPokemons = new ArrayList<>(POKEMONS_LIST);
        sortedPokemons.sort(TestUtils::comparePokemonsByName);
        int size = sortedPokemons.size();
        AtomicInteger i = new AtomicInteger(0);
        pokemons.forEach(pokemon -> {
            assertThat(i.get(), lessThan(size));
            assertThat(pokemon.getId(), equalTo(sortedPokemons.get(i.get()).getId()));
            assertThat(pokemon.getName(), equalTo(sortedPokemons.get(i.get()).getName()));
            i.incrementAndGet();
        });
    }

    static void checkPokemonsList(Collection<Pokemon> actual, Collection<Pokemon> expected) {
        assertThat(actual, hasSize(expected.size()));
        List<Pokemon> sortedActual = new ArrayList<>(actual);
        List<Pokemon> sortedExpected = new ArrayList<>(expected);
        assertThat(sortedActual, hasSize(sortedExpected.size()));
        sortedActual.sort(TestUtils::comparePokemonsByName);
        sortedExpected.sort(TestUtils::comparePokemonsByName);
        for (int i = 0; i < sortedActual.size(); i++) {
            assertThat(sortedActual.get(i).getId(), equalTo(sortedExpected.get(i).getId()));
            assertThat(sortedActual.get(i).getName(), equalTo(sortedExpected.get(i).getName()));
        }
    }

    static void checkPokemonsSortedList(List<Pokemon> actual, List<Pokemon> expected) {
        assertThat(actual, hasSize(expected.size()));
        for (int i = 0; i < actual.size(); i++) {
            assertThat(actual.get(i).getId(), equalTo(expected.get(i).getId()));
            assertThat(actual.get(i).getName(), equalTo(expected.get(i).getName()));
        }
    }

    static List<Pokemon> sortedPokemonsListByName(List<Pokemon> pokemons) {
        pokemons.sort(TestUtils::comparePokemonsByName);
        return pokemons;
    }

    static List<Pokemon> sortedPokemonsListByHpAndName(List<Pokemon> pokemons) {
        pokemons.sort(TestUtils::comparePokemonsByHpAndName);
        return pokemons;
    }

    static List<Pokemon> pokemonsById(int... id) {
        if (id == null || id.length == 0) {
            return Collections.emptyList();
        }
        List<Pokemon> pokemons = new ArrayList<>(id.length);
        for (int i = 0; i < id.length; i++) {
            pokemons.add(POKEMONS[id[i]]);
        }
        return pokemons;
    }

    static int comparePokemonsByName(Pokemon p1, Pokemon p2) {
        return p1.getName().compareTo(p2.getName());
    }

    static int comparePokemonsByHpAndName(Pokemon p1, Pokemon p2) {
        return p1.getHp() < p2.getHp()
                ? -1
                : p1.getHp() > p2.getHp()
                    ? 1
                    : p1.getName().compareTo(p2.getName());
    }

    static void printPokemons(Collection<Pokemon> pokemons) {
        printPokemons("Pokemons", pokemons);
    }

    static void printPokemons(String message, Collection<Pokemon> pokemons) {
        System.out.print(message);
        System.out.println(":");
        pokemons.forEach(pokemon -> {
            System.out.print(" - ");
            System.out.println(pokemon.toString());
        });
    }

    static List<Pokemon> pokemonsList() {
        List<Pokemon> pokemonsList = new ArrayList<>(POKEMONS.length);
        for (int i = 1; i < POKEMONS.length; i++) {
            pokemonsList.add(POKEMONS[i]);
        }
        return pokemonsList;
    }

}
