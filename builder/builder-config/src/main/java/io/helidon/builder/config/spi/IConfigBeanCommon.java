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

package io.helidon.builder.config.spi;

import io.helidon.builder.AttributeVisitor;

/**
 * These methods are common between generated {@link io.helidon.builder.config.ConfigBean}-annotated type, as well
 * as the associated builder for the same.
 *
 * @deprecated this is for internal use only
 */
public interface IConfigBeanCommon extends ConfigProvider {

/*
  Important Note: caution should be exercised to avoid any 0-arg or 1-arg method. This is because it might clash with generated
  methods. If its necessary to have a 0 or 1-arg method then the convention of prefixing the method with two underscores should be
  used.
 */

    /**
     * Returns the {@link io.helidon.builder.config.ConfigBean}-annotated type.
     *
     * @return the config bean type
     */
    Class<?> __configBeanType();

    /**
     * Visits all attributes with the provided {@link io.helidon.builder.AttributeVisitor}.
     *
     * @param visitor        the visitor
     * @param userDefinedCtx any user-defined context
     * @param <T> type of the user defined context
     */
    <T> void visitAttributes(AttributeVisitor<T> visitor,
                             T userDefinedCtx);

}
