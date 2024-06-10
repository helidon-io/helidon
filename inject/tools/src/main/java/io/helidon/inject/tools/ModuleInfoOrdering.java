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

package io.helidon.inject.tools;

/**
 * Used to declare the preferred ordering of the items in the module-info.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public enum ModuleInfoOrdering {
    /**
     * Little or no attempt is made to preserve comments, loaded/created ordering is arranged top-down.
     */
    NATURAL,

    /**
     * Attempt is preserve comments and natural, loaded/created ordering is arranged top-down.
     */
    NATURAL_PRESERVE_COMMENTS,

    /**
     * Little or no attempt is made to preserve comments, ordering is arranged sorted by the target class or package.
     */
    SORTED
}
