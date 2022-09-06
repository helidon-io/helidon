package io.helidon.examples.data.pokemons.dao;

import io.helidon.data.repository.CrudRepository;
import io.helidon.examples.data.pokemons.model.Type;
import jakarta.persistence.EntityManager;

public abstract class TypesDao implements CrudRepository<Type, Integer>  {

    // Or @Inject
    private final EntityManager entityManager;

    public TypesDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

}
