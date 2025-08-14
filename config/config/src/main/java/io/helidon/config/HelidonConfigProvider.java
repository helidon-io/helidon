/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.config;

import io.helidon.common.LazyValue;

/**
 * Service loader provider implementation for common config.
 *
 * @deprecated this class is only for common config, which will be removed
 */
@Deprecated(forRemoval = true, since = "4.3.0")
@SuppressWarnings("removal")
public class HelidonConfigProvider implements io.helidon.common.config.spi.ConfigProvider {
    private static final LazyValue<io.helidon.config.Config> DEFAULT_CONFIG = LazyValue.create(io.helidon.config.Config::create);

    /**
     * This should only be used by service loader and (possibly) tests.
     *
     * @deprecated only for {@link java.util.ServiceLoader}
     */
    @Deprecated
    public HelidonConfigProvider() {
    }

    @Override
    public io.helidon.common.config.Config create() {
        return DEFAULT_CONFIG.get();
    }
}
