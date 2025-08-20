/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.common.config;

/**
 * To be used with {@link io.helidon.common.config.ConfiguredProvider}, each configured service may have a name.
 *
 * @deprecated this class will be moved to {@code helidon-config} module in Helidon 5
 */
@Deprecated(since = "4.3.0")
public interface NamedService {
    /**
     * Name of this implementation, as provided in {@link io.helidon.common.config.ConfiguredProvider#create(Config, String)}.
     *
     * @return name of this service
     */
    String name();

    /**
     * Type of this implementation, to distinguish instances of same type, with different {@link #name()}.
     * Use for example {@link ConfiguredProvider#configKey()} to define the type.
     *
     * @return type of this service
     */
    String type();
}
