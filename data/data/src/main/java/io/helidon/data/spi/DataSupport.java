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

package io.helidon.data.spi;

import io.helidon.data.DataConfig;

/**
 * Implemented by each support (such as Jakarta persistence, Eclipselink native, SQL native etc.).
 * Instances are created through {@link io.helidon.data.spi.DataSupportProvider#create(DataConfig)}.
 */
public interface DataSupport extends AutoCloseable {
    /**
     * Factory to instantiate repositories, specific to the support.
     *
     * @return repository factory
     */
    RepositoryFactory repositoryFactory();

    /**
     * Type of support (such as {@code jakarta}, {@code eclipselink}, {@code sql}).
     *
     * @return type uniquely identifying this support
     */
    String type();

    /**
     * Data config that was used to create this instance.
     *
     * @return data config
     */
    DataConfig dataConfig();
}
