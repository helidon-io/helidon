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

import java.util.List;

import io.helidon.data.api.DataConfig;
import io.helidon.data.api.DataRegistry;

/**
 * Helidon Data provider for specific platform.
 * Implementation of this class serves as factory class to build platform specific implementation
 * of {@link io.helidon.data.api.Data} interface.
 * <p>
 * There is expected to be only one implementation, provided by Helidon Data module.
 * <p>
 * Data provider is discovered through Java {@link java.util.ServiceLoader}.
 */
public interface DataProvider {

    /**
     * Create instance of {@link io.helidon.data.api.Data} interface.
     *
     * @param config data repository configuration, shall not be {@code null}
     * @return new instance of {@link io.helidon.data.api.Data} interface.
     */
    List<DataRegistry> create(List<DataConfig> config);
}
