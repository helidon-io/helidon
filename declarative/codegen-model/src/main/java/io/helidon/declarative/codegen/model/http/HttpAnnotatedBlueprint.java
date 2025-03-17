/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.model.http;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * An element that is annotation within the HTTP REST handling.
 */
@Prototype.Blueprint(createEmptyPublic = false)
interface HttpAnnotatedBlueprint extends ModelElementBlueprint {
    /**
     * List of produced media types (from server endpoint point of view).
     *
     * @return media types
     */
    @Option.Singular
    List<String> produces();

    /**
     * List of consumed media types (from server endpoint point of view).
     *
     * @return media types
     */
    @Option.Singular
    List<String> consumes();

    /**
     * List of configured header values.
     *
     * @return header values
     */
    @Option.Singular
    List<HeaderValue> headers();

    /**
     * List of computed header values.
     *
     * @return computed header values
     */
    @Option.Singular
    List<ComputedHeader> computedHeaders();

    /**
     * Path of the endpoint/method (optional).
     *
     * @return endpoint/method path
     */
    Optional<String> path();
}
