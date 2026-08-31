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
package io.helidon.data.jdbc.tests.declarative.mysql;

import java.util.List;
import java.util.Optional;

import io.helidon.data.jdbc.tests.application.ContactView;
import io.helidon.data.jdbc.tests.contract.AbstractGeneratedRepositoryContract;
import io.helidon.data.jdbc.tests.declarative.repository.ContactRepository;
import io.helidon.data.jdbc.tests.support.TestConfigFactory;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Executes generated repository behavior against MySQL.
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlGeneratedRepositoryTest extends AbstractGeneratedRepositoryContract {
    @Container
    static final MySQLContainer<?> MYSQL = MySqlDeclarativeTestSupport.MYSQL;

    @Override
    protected void beforeStartApplication() {
        TestConfigFactory.config(MySqlDeclarativeTestSupport.config());
    }

    @Override
    protected long assertGeneratedKeyMappingVariants(ContactRepository repository) {
        long scalarKey = repository.insert("scalar-key");
        Optional<Long> optionalKey = repository.insertOptional("optional-key");
        assertThat(repository.findByName("optional-key"),
                   is(Optional.of(new ContactView(optionalKey.orElseThrow(), "optional-key", Optional.empty()))));
        List<Long> listKeys = repository.insertList("list-key");
        assertThat(listKeys.size(), is(1));
        assertThat(repository.findByName("list-key"),
                   is(Optional.of(new ContactView(listKeys.getFirst(), "list-key", Optional.empty()))));

        // MySQL Connector/J exposes only the single auto-increment GENERATED_KEY value from getGeneratedKeys().
        // It does not return the requested ID, NAME, and EMAIL projection, so assert the supported scalar key.
        List<Long> recordKeys = repository.insertList("record-key");
        assertThat(recordKeys.size(), is(1));
        assertThat(repository.findByName("record-key"),
                   is(Optional.of(new ContactView(recordKeys.getFirst(), "record-key", Optional.empty()))));

        // The same Connector/J generated-key result cannot provide the requested NAME column for mapper tests.
        // Assert the exact committed row identified by the returned generated key instead of weakening the check.
        List<Long> markerKeys = repository.insertList("marker-key");
        assertThat(markerKeys.size(), is(1));
        assertThat(repository.findByName("marker-key"),
                   is(Optional.of(new ContactView(markerKeys.getFirst(), "marker-key", Optional.empty()))));
        List<Long> explicitKeys = repository.insertList("explicit-key");
        assertThat(explicitKeys.size(), is(1));
        assertThat(repository.findByName("explicit-key"),
                   is(Optional.of(new ContactView(explicitKeys.getFirst(), "explicit-key", Optional.empty()))));
        return scalarKey;
    }
}
