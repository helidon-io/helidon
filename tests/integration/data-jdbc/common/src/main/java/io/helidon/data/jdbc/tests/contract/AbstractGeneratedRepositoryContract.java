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
package io.helidon.data.jdbc.tests.contract;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.helidon.data.DataException;
import io.helidon.data.jdbc.tests.application.ContactLabel;
import io.helidon.data.jdbc.tests.application.ContactView;
import io.helidon.data.jdbc.tests.application.EqualWeightContact;
import io.helidon.data.jdbc.tests.application.SingleMapperContact;
import io.helidon.data.jdbc.tests.declarative.repository.ContactRepository;
import io.helidon.data.jdbc.tests.declarative.repository.EqualWeightMapperRepository;
import io.helidon.data.jdbc.tests.declarative.repository.FailingMapperRepository;
import io.helidon.data.jdbc.tests.declarative.repository.MissingMapperRepository;
import io.helidon.data.jdbc.tests.declarative.repository.UnregisteredMapperRepository;
import io.helidon.data.jdbc.tests.support.DatabaseFixture;
import io.helidon.service.registry.ServiceRegistryException;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public abstract class AbstractGeneratedRepositoryContract {
    private static final int CONCURRENT_MAPPER_INVOCATIONS = 16;


    /**
     * Proves the generated repository resolves through the registry and
     * executes query, mapping, generated-key, update, and transaction paths
     * against the real database.
     */
    @Test
    protected void executesGeneratedRepositoryThroughServiceRegistryAndLocalTransactions() {
        ServiceRegistryManager manager = startApplication();
        try {
            ContactRepository repository = manager.registry().get(ContactRepository.class);

            assertThat(repository.findAll(),
                       is(List.of(new ContactView(1, "alpha", Optional.of("alpha@example.test")),
                                  new ContactView(2, "beta", Optional.empty()))));
            assertThat(repository.findAllReordered(),
                       is(List.of(new ContactView(1, "alpha", Optional.of("alpha@example.test")),
                                  new ContactView(2, "beta", Optional.empty()))));
            assertThat(repository.email(1), is(Optional.of("alpha@example.test")));
            assertThat(repository.email(2), is(Optional.empty()));
            assertThat(repository.idsWithDuplicateUnusedLabels(), is(List.of(1L, 2L)));

            DataException ambiguousLabel = assertThrows(DataException.class,
                                                        () -> repository.ambiguousRecordLabels(1));
            assertThat(ambiguousLabel.getMessage(),
                       is("The result contains more than one column labeled 'name'."));
            DataException missingLabel = assertThrows(DataException.class,
                                                      () -> repository.missingRecordLabel(1));
            assertThat(missingLabel.getMessage(), is("The result does not contain a column labeled 'name'."));

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

            assertThat(repository.optionalEmailFilter("alpha@example.test"),
                       is(List.of(new ContactView(1, "alpha", Optional.of("alpha@example.test")))));
            assertThat(repository.optionalEmailFilter(null),
                       is(List.of(new ContactView(1, "alpha", Optional.of("alpha@example.test")),
                                  new ContactView(2, "beta", Optional.empty()))));
            assertThat(repository.nullSafeEmail("alpha@example.test"),
                       is(List.of(new ContactView(1, "alpha", Optional.of("alpha@example.test")))));
            assertThat(repository.nullSafeEmail(null),
                       is(List.of(new ContactView(2, "beta", Optional.empty()))));

            long scalarKey = assertGeneratedKeyMappingVariants(repository);
            assertThat(repository.rename("renamed", scalarKey), is(1));
            assertThat(repository.delete(scalarKey), is(1L));

            Tx.transaction(Tx.Type.REQUIRED, () -> {
                repository.insert("committed");
                return null;
            });
            ContactView committed = repository.findByName("committed").orElseThrow();
            assertThat(committed, is(new ContactView(committed.id(), "committed", Optional.empty())));
            int beforeRollback = repository.findAll().size();
            assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
                repository.insert("rolled-back");
                throw new IllegalStateException("force rollback");
            }));
            assertThat(repository.findAll().size(), is(beforeRollback));
            assertThat(repository.findByName("rolled-back"), is(Optional.empty()));
            assertThat(repository.findByName("committed"), is(Optional.of(committed)));
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Proves equal-weight marker mappers are selected deterministically rather
     * than according to service discovery order.
     */
    @Test
    protected void selectsEqualWeightMarkerMapperDeterministically() {
        ServiceRegistryManager manager = startApplication();
        try {
            EqualWeightMapperRepository repository = manager.registry().get(EqualWeightMapperRepository.class);

            assertThat(repository.find(1), is(new EqualWeightContact("alpha:alpha")));
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Proves repository activation fails when either marker or explicitly
     * selected mapper services cannot be resolved.
     */
    @Test
    protected void rejectsMissingMarkerAndExplicitMapperServicesDuringRepositoryActivation() {
        ServiceRegistryManager manager = startApplication();
        try {
            assertThrows(ServiceRegistryException.class,
                         () -> manager.registry().get(MissingMapperRepository.class));
            assertThrows(ServiceRegistryException.class,
                         () -> manager.registry().get(UnregisteredMapperRepository.class));
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Proves failure activating the preferred mapper is not hidden by falling
     * back to a lower-weight mapper.
     */
    @Test
    protected void doesNotFallBackWhenPreferredMapperActivationFails() {
        ServiceRegistryManager manager = startApplication();
        try {
            assertThrows(ServiceRegistryException.class,
                         () -> manager.registry().get(FailingMapperRepository.class));
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Proves the registry-provided singleton mapper remains correct when a
     * generated repository invokes it concurrently from virtual threads.
     */
    @Test
    protected void invokesSharedSingletonMapperConcurrently() throws Exception {
        ServiceRegistryManager manager = startApplication();
        try {
            ContactRepository repository = manager.registry().get(ContactRepository.class);
            Callable<ContactLabel> invocation = () -> repository.mapped(1);
            // This checks singleton mapper reuse across concurrent virtual-thread calls without turning the
            // repository test into an Oracle listener capacity test.
            List<Callable<ContactLabel>> invocations =
                    Collections.nCopies(CONCURRENT_MAPPER_INVOCATIONS, invocation);

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

    /**
     * Proves generated query, update, and key operations obey each individual
     * transaction policy both outside and inside an active transaction.
     */
    @Test
    protected void executesGeneratedOperationKindsAcrossEveryPropagationType() {
        ServiceRegistryManager manager = startApplication();
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

    /**
     * Allows a database-specific leaf test to publish dynamic configuration before the registry starts.
     */
    protected void beforeStartApplication() {
    }

    /**
     * Proves generated repository methods can map scalar, optional, list,
     * record, marker-mapped, and explicitly mapped generated-key results.
     *
     * @param repository generated contact repository
     * @return scalar key used by the caller for update and delete assertions
     */
    protected long assertGeneratedKeyMappingVariants(ContactRepository repository) {
        long scalarKey = repository.insert("scalar-key");
        Optional<Long> optionalKey = repository.insertOptional("optional-key");
        assertThat(repository.findByName("optional-key"),
                   is(Optional.of(new ContactView(optionalKey.orElseThrow(), "optional-key", Optional.empty()))));
        List<Long> listKeys = repository.insertList("list-key");
        assertThat(listKeys.size(), is(1));
        assertThat(repository.findByName("list-key"),
                   is(Optional.of(new ContactView(listKeys.getFirst(), "list-key", Optional.empty()))));
        ContactView recordKey = repository.insertRecord("record-key", "record@example.test");
        assertThat(recordKey,
                   is(new ContactView(recordKey.id(), "record-key", Optional.of("record@example.test"))));
        assertThat(repository.findByName("record-key"), is(Optional.of(recordKey)));
        ContactLabel markerKey = repository.insertMapped("marker-key");
        assertThat(markerKey, is(new ContactLabel(markerKey.id(), "preferred:marker-key")));
        assertThat(repository.findByName("marker-key"),
                   is(Optional.of(new ContactView(markerKey.id(), "marker-key", Optional.empty()))));
        Optional<ContactLabel> markerOptionalKey = repository.insertMappedOptional("marker-optional-key");
        ContactLabel markerOptional = markerOptionalKey.orElseThrow();
        assertThat(markerOptional, is(new ContactLabel(markerOptional.id(), "preferred:marker-optional-key")));
        List<ContactLabel> markerListKeys = repository.insertMappedList("marker-list-key");
        assertThat(markerListKeys, is(List.of(new ContactLabel(markerListKeys.getFirst().id(),
                                                               "preferred:marker-list-key"))));
        ContactLabel explicitKey = repository.insertExplicitlyMapped("explicit-key");
        assertThat(explicitKey, is(new ContactLabel(explicitKey.id(), "explicit:explicit-key")));
        Optional<ContactLabel> explicitOptionalKey =
                repository.insertExplicitlyMappedOptional("explicit-optional-key");
        ContactLabel explicitOptional = explicitOptionalKey.orElseThrow();
        assertThat(explicitOptional,
                   is(new ContactLabel(explicitOptional.id(), "explicit:explicit-optional-key")));
        List<ContactLabel> explicitListKeys = repository.insertExplicitlyMappedList("explicit-list-key");
        assertThat(explicitListKeys, is(List.of(new ContactLabel(explicitListKeys.getFirst().id(),
                                                                 "explicit:explicit-list-key"))));
        return scalarKey;
    }

    private static void exerciseGeneratedOperations(ContactRepository repository, Tx.Type type) {
        Tx.transaction(type, () -> {
            repository.findAll();
            repository.rename("alpha", 1);
            repository.insert("propagation-" + type);
            return null;
        });
    }

    private ServiceRegistryManager startApplication() {
        beforeStartApplication();
        ServiceRegistryManager manager = ServiceRegistryManager.start();
        manager.registry().get(DatabaseFixture.class).reset();
        return manager;
    }

}
