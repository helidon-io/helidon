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

/**
 * Types that are Pico API/consumer facing. The Pico API provide types that are generally useful at compile time
 * to assign special meaning to the type. In this way it also helps with readability and intentions of the code itself.
 * <ul>
 * <li>{@link io.helidon.pico.api.Contract} - signifies that the type can be used for lookup in the service registry.</li>
 * <li>{@link io.helidon.pico.api.ExternalContracts} - same as Contract, but applied to the implementation class instead.</li>
 * <li>{@link io.helidon.pico.api.RunLevel} - ascribes meaning for when the service should start.</li>
 * </ul>
 */
package io.helidon.pico.api;
