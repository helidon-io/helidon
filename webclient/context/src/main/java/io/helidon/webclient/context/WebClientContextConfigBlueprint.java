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

package io.helidon.webclient.context;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration of WebClient transport level propagation of context values.
 */
@Prototype.Blueprint
@Prototype.Configured
interface WebClientContextConfigBlueprint extends Prototype.Factory<WebClientContextService> {
    /**
     * Name of this instance.
     *
     * @return instance name
     */
    @Option.Default("context")
    String name();

    /**
     * List of propagation records.
     *
     * @return records configuration
     */
    @Option.Singular
    @Option.Configured
    List<ContextRecord> records();
}