/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.configdriven.api;

/**
 * Instance, that can be (possibly) named.
 *
 * @param instance instance of config bean
 * @param name the instance may have a name, if this is the default (not named), the name is set to {@value #DEFAULT_NAME}
 * @param <T> type of the instance
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public record NamedInstance<T>(T instance, String name) {
    /**
     * Default name of an instance that is not named for the purpose of injection, for example.
     */
    public static final String DEFAULT_NAME = "@default";
}
