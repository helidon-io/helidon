package io.helidon.integrations.micronaut.cdi.data.app;

import java.util.List;
import java.util.Optional;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.H2)
public interface DbOwnerRepository extends CrudRepository<Owner, Long> {
    @Override
    List<Owner> findAll();

    Optional<Owner> findByName(String name);
}
