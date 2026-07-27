/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc.tests;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.helidon.data.DataException;
import io.helidon.service.registry.ServiceRegistryException;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratedRepositoryTest {

    @Test
    void executesGeneratedRepositoryThroughServiceRegistryAndLocalTransactions() {
        ServiceRegistryManager manager = ServiceRegistryManager.start();
        try {
            ContactRepository repository = manager.registry().get(ContactRepository.class);

            assertThat(repository.findAll(),
                       is(List.of(new ContactView(1, "alpha", Optional.of("alpha@example.test")),
                                  new ContactView(2, "beta", Optional.empty()))));
            assertThat(repository.email(1), is(Optional.of("alpha@example.test")));
            assertThat(repository.email(2), is(Optional.empty()));
            assertThat(repository.idsWithDuplicateUnusedLabels(), is(List.of(1L, 2L)));

            DataException ambiguousLabel = assertThrows(DataException.class,
                                                        () -> repository.ambiguousRecordLabels(1));
            assertThat(ambiguousLabel.getMessage(), is("Ambiguous result column label: name"));
            DataException missingLabel = assertThrows(DataException.class,
                                                      () -> repository.missingRecordLabel(1));
            assertThat(missingLabel.getMessage(), is("Result column label not found: name"));

            assertThat(repository.mapped(1), is(new ContactLabel(1, "preferred:alpha")));
            assertThat(repository.singleMapped(1), is(new SingleMapperContact("single:alpha")));
            assertThat(repository.mappedOptional(1),
                       is(Optional.of(new ContactLabel(1, "preferred:alpha"))));
            assertThat(repository.mappedOptional(Long.MAX_VALUE), is(Optional.empty()));
            assertThat(repository.mappedList().subList(0, 2),
                       is(List.of(new ContactLabel(1, "preferred:alpha"),
                                  new ContactLabel(2, "preferred:beta"))));
            assertThat(repository.explicitlyMapped(1), is(new ContactLabel(1, "explicit:alpha")));
            assertThat(repository.explicitlyMappedOptional(1),
                       is(Optional.of(new ContactLabel(1, "explicit:alpha"))));
            assertThat(repository.explicitlyMappedOptional(Long.MAX_VALUE), is(Optional.empty()));
            assertThat(repository.explicitlyMappedList().subList(0, 2),
                       is(List.of(new ContactLabel(1, "explicit:alpha"),
                                  new ContactLabel(2, "explicit:beta"))));
            IllegalStateException mapperFailure = assertThrows(IllegalStateException.class,
                                                               () -> repository.mapperFailure(1));
            assertThat(mapperFailure.getMessage(), is("deliberate mapper failure"));
            assertThat(repository.email(1), is(Optional.of("alpha@example.test")));

            assertThat(repository.optionalEmailFilter("alpha@example.test").size(), is(1));
            assertThat(repository.optionalEmailFilter(null).size(), is(2));
            assertThat(repository.nullSafeEmail("alpha@example.test").size(), is(1));
            assertThat(repository.nullSafeEmail(null),
                       is(List.of(new ContactView(2, "beta", Optional.empty()))));

            long scalarKey = repository.insert("scalar-key");
            assertThat(repository.insertOptional("optional-key").isPresent(), is(true));
            assertThat(repository.insertList("list-key").size(), is(1));
            ContactView recordKey = repository.insertRecord("record-key", "record@example.test");
            assertThat(recordKey.name(), is("record-key"));
            assertThat(recordKey.email(), is(Optional.of("record@example.test")));
            assertThat(repository.insertMapped("marker-key").label(), is("preferred:marker-key"));
            assertThat(repository.insertMappedOptional("marker-optional-key").isPresent(), is(true));
            assertThat(repository.insertMappedList("marker-list-key").size(), is(1));
            assertThat(repository.insertExplicitlyMapped("explicit-key").label(), is("explicit:explicit-key"));
            assertThat(repository.insertExplicitlyMappedOptional("explicit-optional-key").isPresent(), is(true));
            assertThat(repository.insertExplicitlyMappedList("explicit-list-key").size(), is(1));
            assertThat(repository.rename("renamed", scalarKey), is(1));
            assertThat(repository.delete(scalarKey), is(1L));

            Tx.transaction(Tx.Type.REQUIRED, () -> {
                repository.insert("committed");
                return null;
            });
            int beforeRollback = repository.findAll().size();
            assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
                repository.insert("rolled-back");
                throw new IllegalStateException("force rollback");
            }));
            assertThat(repository.findAll().size(), is(beforeRollback));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void selectsEqualWeightMarkerMapperDeterministically() {
        ServiceRegistryManager manager = ServiceRegistryManager.start();
        try {
            EqualWeightMapperRepository repository = manager.registry().get(EqualWeightMapperRepository.class);

            assertThat(repository.find(1), is(new EqualWeightContact("alpha:alpha")));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void rejectsMissingMarkerAndExplicitMapperServicesDuringRepositoryActivation() {
        ServiceRegistryManager manager = ServiceRegistryManager.start();
        try {
            assertThrows(ServiceRegistryException.class,
                         () -> manager.registry().get(MissingMapperRepository.class));
            assertThrows(ServiceRegistryException.class,
                         () -> manager.registry().get(UnregisteredMapperRepository.class));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void doesNotFallBackWhenPreferredMapperActivationFails() {
        ServiceRegistryManager manager = ServiceRegistryManager.start();
        try {
            assertThrows(ServiceRegistryException.class,
                         () -> manager.registry().get(FailingMapperRepository.class));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void invokesSharedSingletonMapperConcurrently() throws Exception {
        ServiceRegistryManager manager = ServiceRegistryManager.start();
        try {
            ContactRepository repository = manager.registry().get(ContactRepository.class);
            Callable<ContactLabel> invocation = () -> repository.mapped(1);
            List<Callable<ContactLabel>> invocations = java.util.Collections.nCopies(100, invocation);

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<ContactLabel>> results = executor.invokeAll(invocations);
                for (Future<ContactLabel> result : results) {
                    assertThat(result.get(), is(new ContactLabel(1, "preferred:alpha")));
                }
            }
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void rejectsAnUpdateCountOutsideThePrimitiveIntRange() {
        io.helidon.data.jdbc.JdbcClient client = org.mockito.Mockito.mock(io.helidon.data.jdbc.JdbcClient.class);
        io.helidon.data.jdbc.JdbcClient.Statement statement =
                org.mockito.Mockito.mock(io.helidon.data.jdbc.JdbcClient.Statement.class);
        org.mockito.Mockito.when(client.create(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
        org.mockito.Mockito.when(statement.bind(org.mockito.ArgumentMatchers.anyInt(),
                                                org.mockito.ArgumentMatchers.any()))
                .thenReturn(statement);
        org.mockito.Mockito.when(statement.execute()).thenReturn(Long.MAX_VALUE);
        ContactRepository repository = new ContactRepository__Jdbc(client,
                                                                    row -> new ContactLabel(0, ""),
                                                                    row -> new SingleMapperContact(""),
                                                                    new ExplicitContactMapper(),
                                                                    new ThrowingContactMapper());

        assertThrows(ArithmeticException.class, () -> repository.rename("overflow", 1));
    }

    @Test
    void executesGeneratedOperationKindsAcrossEveryPropagationType() {
        ServiceRegistryManager manager = ServiceRegistryManager.start();
        try {
            ContactRepository repository = manager.registry().get(ContactRepository.class);

            exerciseGeneratedOperations(repository, Tx.Type.REQUIRED);
            exerciseGeneratedOperations(repository, Tx.Type.NEW);
            exerciseGeneratedOperations(repository, Tx.Type.SUPPORTED);
            exerciseGeneratedOperations(repository, Tx.Type.NEVER);
            exerciseGeneratedOperations(repository, Tx.Type.UNSUPPORTED);
            assertThrows(TxException.class,
                         () -> exerciseGeneratedOperations(repository, Tx.Type.MANDATORY));

            for (Tx.Type type : List.of(Tx.Type.REQUIRED,
                                        Tx.Type.MANDATORY,
                                        Tx.Type.SUPPORTED,
                                        Tx.Type.NEW,
                                        Tx.Type.UNSUPPORTED)) {
                Tx.transaction(Tx.Type.REQUIRED, () -> {
                    exerciseGeneratedOperations(repository, type);
                    return null;
                });
            }
            Tx.transaction(Tx.Type.REQUIRED, () -> {
                assertThrows(TxException.class,
                             () -> exerciseGeneratedOperations(repository, Tx.Type.NEVER));
                return null;
            });
        } finally {
            manager.shutdown();
        }
    }

    private static void exerciseGeneratedOperations(ContactRepository repository, Tx.Type type) {
        Tx.transaction(type, () -> {
            repository.findAll();
            repository.rename("alpha", 1);
            repository.insert("propagation-" + type);
            return null;
        });
    }
}
