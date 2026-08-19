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
package io.helidon.data.jdbc;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.common.Size;

/**
 * Configuration blueprint for bounded persistence unit bootstrap scripts.
 */
@Api.Preview
@Prototype.Blueprint(decorator = JdbcProviderPropertiesSupport.ScriptDecorator.class)
@Prototype.Configured
interface JdbcScriptConfigBlueprint {

    /**
     * Maximum number of bytes accepted from one bootstrap resource.
     *
     * @return maximum resource size
     */
    @Option.Configured
    @Option.Default("8 MiB")
    Size maxResourceSize();

    /**
     * Maximum number of bytes accepted across the complete bootstrap plan.
     *
     * @return maximum total size
     */
    @Option.Configured
    @Option.Default("16 MiB")
    Size maxTotalSize();

    /**
     * Maximum number of executable statements in the complete bootstrap plan.
     *
     * @return maximum statement count
     */
    @Option.Configured
    @Option.DefaultInt(10_000)
    int maxStatements();
}
