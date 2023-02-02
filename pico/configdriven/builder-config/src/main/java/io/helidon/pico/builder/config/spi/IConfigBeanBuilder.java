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

import io.helidon.common.config.Config;

/**
 * Every {@link io.helidon.pico.builder.config.ConfigBean}-annotated *builder* type will implement this contract.
 *
 * @deprecated
 */
public interface IConfigBeanBuilder extends IConfigBeanCommon {

/*
  Important Note: caution should be exercised to avoid any 0-arg or 1-arg method. This is because it might clash with generated
  methods. If its necessary to have a 0 or 1-arg method then the convention of prefixing the method with two underscores should be
  used.
 */

    /**
     * Copy values from the config, optionally applying the default resolver and validator.
     *
     * @param cfg                   the config to initialize the builder values
     * @param resolveAndValidate    if called will resolve and validate
     *
     * @see ConfigResolver
     * @see ConfigBeanBuilderValidatorHolder
     * @throws java.lang.IllegalStateException if there are any resolution or validation errors
     */
    void acceptConfig(Config cfg,
                      boolean resolveAndValidate);

    /**
     * Copy values from an existing builder.
     *
     * @param cfg       the config to initialize the builder values
     * @param resolver  the resolver
     * @param validator the validator
     * @throws java.lang.IllegalStateException if there are any resolution or validation errors
     */
    void acceptConfig(Config cfg,
                    ConfigResolver resolver,
                    ConfigBeanBuilderValidator<?> validator);

}
