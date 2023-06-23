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

import java.util.Optional;

import io.helidon.common.config.Config;

/**
 * Every {@link io.helidon.builder.config.ConfigBean}-annotated type will also implement this contract.
 */
public interface GeneratedConfigBean extends GeneratedConfigBeanCommon {

/*
  Important Note: caution should be exercised to avoid any 0-arg or 1-arg method. This is because it might clash with generated
  methods. If its necessary to have a 0 or 1-arg method then the convention of prefixing the method with two underscores should be
  used.
 */

    /**
     * Returns the instance id assigned to this bean.
     * Note that the instance id is typically assigned the {@link Config#key()} in most circumstances.
     *
     * @return the instance id assigned to this bean
     */
    String __instanceId();

    /**
     * Returns the {@link Config#name()} if available.
     *
     * @return the config name or else empty
     */
    Optional<String> __name();

    /**
     * Returns the generated config bean meta information.
     *
     * @return the config bean meta information
     */
    MetaConfigBeanInfo __metaInfo();

}
