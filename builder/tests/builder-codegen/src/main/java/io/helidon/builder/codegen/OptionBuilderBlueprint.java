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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeName;

/**
 * If an option itself has a builder, we add a method with {@code Consumer<Builder>}.
 * <p>
 * The type must have a {@code builder} method that returns a builder type.
 * The builder then must have a {@code build} method that returns the option type, or a {@code buildPrototype} method.
 */
@Prototype.Blueprint
interface OptionBuilderBlueprint {
    /**
     * Name of the static builder method, or {@code <init>} to identify a constructor should be used.
     * If a method name is defined, it is expected to be on the type of the option. If constructor is defined,
     * it is expected to be an accessible constructor on the {@link #builderType()}.
     *
     * @return name of the method
     */
    @Option.Default("builder")
    String builderMethodName();
    /**
     * Type of the builder.
     *
     * @return type of the builder
     */
    TypeName builderType();

    /**
     * Name of the build method ({@code build} or {@code buildPrototype}).
     *
     * @return builder build method name
     */
    String buildMethodName();
}
