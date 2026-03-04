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
package io.helidon.config;

/**
 * To be used with {@link io.helidon.config.ConfiguredProvider}, each configured service may have a name.
 */
@SuppressWarnings("removal")
public interface NamedService extends io.helidon.common.config.NamedService {
    /**
     * Name of this implementation, as provided in
     * {@link io.helidon.config.ConfiguredProvider#create(io.helidon.common.config.Config, String)}.
     *
     * @return name of this service
     */
    @Override
    String name();

    /**
     * Type of this implementation, to distinguish instances of same type, with different {@link #name()}.
     * Use for example {@link io.helidon.config.ConfiguredProvider#configKey()} to define the type.
     *
     * @return type of this service
     */
    @Override
    String type();
}
