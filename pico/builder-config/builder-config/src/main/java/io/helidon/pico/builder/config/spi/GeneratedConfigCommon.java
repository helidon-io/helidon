/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import io.helidon.common.config.spi.ConfigProvider;
import io.helidon.pico.builder.AttributeVisitor;

/**
 * These methods are in common between generated config bean and config bean builder types.
 *
 * @deprecated this is for internal use only
 */
public interface GeneratedConfigCommon extends ConfigProvider {

/*
  Important Note: caution should be exercised to avoid any 0-arg or 1-arg method. This is because it might clash with generated
  methods. If its necessary to have a 0 or 1-arg method then the convention of prefixing the method with two underscores should be
  used.
 */

    /**
     * Returns the configuration assigned to the generated config bean.
     *
     * @return the configuration assigned
     */
    @Override
    Optional<Config> __config();

    /**
     * Returns the {@link io.helidon.pico.builder.config.ConfigBean}-annotated type.
     *
     * @return the config bean type
     */
    Class<?> __configBeanType();

    /**
     * Visits all attributes with the provided {@link io.helidon.pico.builder.AttributeVisitor}.
     *
     * @param visitor           the visitor
     * @param userDefinedCtx    any user-defined context
     */
    void visitAttributes(AttributeVisitor visitor,
                         Object userDefinedCtx);

}
