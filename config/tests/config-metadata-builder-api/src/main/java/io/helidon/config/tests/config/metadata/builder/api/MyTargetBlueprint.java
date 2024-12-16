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

package io.helidon.config.tests.config.metadata.builder.api;

import io.helidon.builder.api.Description;
import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Blueprint
@Prototype.Configured
@Description("builder")
interface MyTargetBlueprint extends MyAbstractBlueprint {
    String CONSTANT = "42";

    @Description("message description")
    @Option.Configured
    @Option.Default("message")
    String message();

    @Description("type description")
    @Option.Configured
    @Option.DefaultInt(42)
    @Option.AllowedValue(value = "42", description = "answer")
    @Option.AllowedValue(value = "0", description = "no answer")
    int type();

    @Description("Ignored option")
    String ignored();

    /**
     * Description.
     * {@code technical}
     * {@link MyTarget#ignored()}
     * {@linkplain MyTarget#ignored()}
     * {@value #CONSTANT}
     *
     * @return some value
     * @see MyTarget#message()
     */
    @Option.Configured
    String javadoc();
}
