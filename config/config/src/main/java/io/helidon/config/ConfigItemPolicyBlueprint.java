/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import io.helidon.builder.api.Prototype;

/**
 * Configuration item policy.
 * Contains information about the given item to improve its handling.
 */
@Prototype.Blueprint
interface ConfigItemPolicyBlueprint {

    /**
     * Whether to cache this handled config item or not.
     * If overall caching is disabled, this will not turn it on even if set to true.
     *
     * @return whether to cache handled config item
     */
    boolean cacheItem();

    /**
     * Handled config item.
     *
     * @return config item
     */
    String item();

}
