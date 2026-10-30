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
package io.helidon.tests.integration.data.jdbc.contract;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.helidon.data.jdbc.JdbcClient;
import io.helidon.tests.integration.data.jdbc.declarative.repository.BatchRepository;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Executes generated source adapters through the same real-driver batch contract.
 */
public abstract class AbstractDeclarativeBatchContract extends AbstractJdbcBatchContract {
    @Override
    protected final JdbcClient.BatchResult insert(List<String> rows, String email) {
        return service(BatchRepository.class).insert(rows, email);
    }

    @Test
    protected final void executesRecordsRepeatedMarkersAndAlignedArrays() {
        BatchRepository repository = service(BatchRepository.class);
        repository.records(List.of(new BatchRepository.Change("record-one", null, 1),
                                   new BatchRepository.Change("record-two", "email", 2)));
        assertThat(client().create("SELECT NAME FROM CONTACT WHERE ID = 1").map(String.class).one(), is("record-one"));
        repository.aligned(Arrays.asList("array-one", "array-two").iterator(), null, 1, 2);
        assertThat(client().create("SELECT EMAIL FROM CONTACT WHERE ID = 1").map(String.class).optional(),
                   is(Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> repository.aligned(List.of("mismatch").iterator(), "email", 1, 2));
        assertThat(client().create("SELECT NAME FROM CONTACT WHERE ID = 1").map(String.class).one(), is("array-one"));
    }
}
