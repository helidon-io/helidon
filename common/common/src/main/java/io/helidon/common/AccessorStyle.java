/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.common;

/**
 * Accessor styles supported by Helidon modules.
 */
public enum AccessorStyle {

    /**
     * Accessor are identified without the get/set prefix same as in Java records.
     * <p>
     * Examples:
     * <ul>
     *     <li>{@code int yearOfBirth()}</li>
     *     <li>{@code void yearOfBirth(int year}</li>
     *     <li>{@code boolean enabled()}</li>
     *     <li>{@code void enabled(boolean enabled)}</li>
     * </ul>
     */
    RECORD,

    /**
     * Accessor are identified with the get/set prefix as in Java beans.
     * <p>
     * Examples:
     * <ul>
     *     <li>{@code int getYearOfBirth()}</li>
     *     <li>{@code void setYearOfBirth(int year}</li>
     *     <li>{@code boolean isEnabled()}</li>
     *     <li>{@code void setEnabled(boolean enabled)}</li>
     * </ul>
     */
    BEAN,

    /**
     * The style of accessors is automatically detected.
     * First bean and if no accessor is found, record style is tested.
     * This enum value is only relevant when "guessing" from an existing type. For cases when we generate code,
     * one of the other styles must be chosen.
     */
    AUTO

}
