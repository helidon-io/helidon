package io.helidon.examples.data.pokemons.dao;

import io.helidon.data.repository.CrudRepository;
import io.helidon.examples.data.pokemons.model.Pokemon;
import jakarta.persistence.EntityManager;

import java.util.List;


public abstract class PokemonDao implements CrudRepository<Pokemon, Integer> {

    // Or @Inject
    private final EntityManager entityManager;

    public PokemonDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public abstract List<Pokemon> findByName(String name);

}
