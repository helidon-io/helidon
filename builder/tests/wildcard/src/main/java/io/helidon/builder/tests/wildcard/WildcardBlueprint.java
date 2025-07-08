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

package io.helidon.builder.tests.wildcard;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Blueprint
@Prototype.Configured
interface WildcardBlueprint {
    /**
     * A list of classes with a wildcard. This should generate:
     * <ul>
     *   <li>{@code addExtension(Class<?>)}</li>
     *   <li>{@code addExtensions(List<? extends Class<?>>)}</li>
     *   <li>other methods as usual</li>
     * </ul>
     * @return list of extensions
     */
    @Option.Singular
    @Option.Configured
    List<Class<?>> extensions();

    /**
     * A single class.
     *
     * @return a single class
     */
    @Option.Configured
    Class<?> plugin();
}
