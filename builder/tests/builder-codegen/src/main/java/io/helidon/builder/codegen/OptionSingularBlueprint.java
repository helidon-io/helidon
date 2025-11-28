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

import io.helidon.builder.api.Prototype;

/**
 * Definition of a singular option.
 */
@Prototype.Blueprint(detach = true)
interface OptionSingularBlueprint {

    /**
     * Singular form of the option name.
     * For {@code lines}, this would be {@code line}.
     * For {@code properties}, this should be {@code property}, so we allow customization by the user.
     *
     * @return singular name
     */
    String name();

    /**
     * Name of the singular setter method.
     *
     * @return method name
     */
    String methodName();
}
