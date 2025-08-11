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
package io.helidon.data.jakarta.persistence.codegen;

import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.common.spi.PersistenceGeneratorProvider;

/**
 * Jakarta Persistence generator provider.
 */
public class JakartaPersistenceGeneratorProvider implements PersistenceGeneratorProvider {

    /**
     * Creates an instance of Jakarta Persistence generator provider.
     */
    public JakartaPersistenceGeneratorProvider() {
    }

    @Override
    public PersistenceGenerator create() {
        return new JakartaPersistenceGenerator();
    }

}
