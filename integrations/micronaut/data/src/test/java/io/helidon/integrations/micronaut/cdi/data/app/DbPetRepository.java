package io.helidon.integrations.micronaut.cdi.data.app;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.PageableRepository;

@JdbcRepository(dialect = Dialect.H2)
public abstract class DbPetRepository implements PageableRepository<Pet, UUID> {

    public abstract List<NameDTO> list(Pageable pageable);

    @Join("owner")
    public abstract Optional<Pet> findByName(String name);
}
