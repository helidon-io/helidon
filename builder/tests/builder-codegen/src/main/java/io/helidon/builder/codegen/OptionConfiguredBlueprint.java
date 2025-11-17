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

package io.helidon.builder.codegen;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Setup of configured option.
 */
@Prototype.Blueprint(detach = true)
interface OptionConfiguredBlueprint {
    /**
     * Config key to use.
     *
     * @return config key
     */
    String configKey();

    /**
     * Whether to merge the key with the current object.
     *
     * @return whether to merge, defaults to {@code false}, i.e. this option will have its own key, named {@link #configKey()}
     */
    @Option.DefaultBoolean(false)
    boolean merge();

    /**
     * Whether to traverse the config node when creating a map.
     *
     * @return whether to traverse config, defaults to {@code true}
     */
    @Option.DefaultBoolean(true)
    boolean traverse();

    /**
     * Factory method for this option. Factory method will be discovered from
     * {@link io.helidon.builder.codegen.PrototypeInfo#configFactories()}.
     *
     * @return config factory method if defined
     */
    Optional<FactoryMethod> factoryMethod();
}
