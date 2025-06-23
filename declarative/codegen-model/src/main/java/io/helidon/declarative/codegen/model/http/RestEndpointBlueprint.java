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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * A REST endpoint may be a client endpoint or a server endpoint.
 */
@Prototype.Blueprint(createEmptyPublic = false)
interface RestEndpointBlueprint extends HttpAnnotatedBlueprint {
    /**
     * Methods that have a rest annotation (HTTP method).
     *
     * @return all rest methods defined
     */
    @Option.Singular
    List<RestMethod> methods();
}
