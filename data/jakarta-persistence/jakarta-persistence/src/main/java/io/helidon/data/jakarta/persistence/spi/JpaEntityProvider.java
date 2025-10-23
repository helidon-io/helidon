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
package io.helidon.data.jakarta.persistence.spi;

import io.helidon.service.registry.Service;

/**
 * Persistence entity provider contract.
 * Alternative persistence context initialization for common persistence providers, e.g.
 * Jakarta Persistence compliant runtime without provider specific extension.
 * <p>
 * Entity providers are discovered using service registry.
 * Entity classes may be provided as class names in Config.
 * This interface allows alternative entity classes configuration.
 *
 * @param <T> type of the entity supported by this provider
 */
@Service.Contract
public interface JpaEntityProvider<T> {
    /**
     * Persistence entity class.
     *
     * @return class of entity described by this metadata provider
     */
    Class<T> entityClass();
}
