/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.config.spi;

import java.util.Optional;

import io.helidon.common.config.Config;

/**
 * Generated {@link io.helidon.pico.builder.config.ConfigBean}-annotated types and the associated builder types implement
 * this contract.
 */
@FunctionalInterface
public interface ConfigProvider /* extends Supplier<Config>*/ {
/*
  Important Note: caution should be exercised to avoid any 0-arg or 1-arg method. This is because it might clash with generated
  methods. If its necessary to have a 0 or 1-arg method then the convention of prefixing the method with two underscores should be
  used.

  Conceptually this is the same as {@code Supplier<Config>}. However, the get() method imposed by supplier may
  clash with generated code
 */

    /**
     * Optionally provides a configuration instance.
     *
     * @return the optional configuration
     */
    // note that this needs to have double underscore since it is available in generated code
    Optional<Config> __config();

}
