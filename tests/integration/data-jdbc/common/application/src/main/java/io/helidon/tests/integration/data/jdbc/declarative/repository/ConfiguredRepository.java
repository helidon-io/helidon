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
package io.helidon.tests.integration.data.jdbc.declarative.repository;

import java.util.List;
import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.tests.integration.data.jdbc.application.ConfiguredOperations;
import io.helidon.tests.integration.data.jdbc.application.ContactView;

/**
 * The same compiled repository executes configured statements against multiple deployment catalogs.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface ConfiguredRepository extends ConfiguredOperations {

    @Override
    @Jdbc.Statement(name = "find")
    List<ContactView> find(String name, String email);

    @Override
    @Jdbc.Statement(name = "rename")
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long rename(String name, long id);

    @Override
    @Jdbc.Statement(name = "insert")
    @Jdbc.GeneratedKeys("id")
    Optional<Long> insert(String value);
}
