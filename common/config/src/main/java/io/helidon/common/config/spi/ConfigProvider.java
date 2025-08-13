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

package io.helidon.common.config.spi;

/**
 * Service loader provider interface to discover config implementation that would be used to
 * obtain a default configuration instance.
 *
 * @deprecated there is no replacement for this type, use factory methods on the implementation directly
 */
@SuppressWarnings("removal")
@Deprecated(forRemoval = true, since = "4.3.0")
public interface ConfigProvider {
    /**
     * Create the default configuration instance.
     *
     * @return a new configuration
     */
    io.helidon.common.config.Config create();
}
