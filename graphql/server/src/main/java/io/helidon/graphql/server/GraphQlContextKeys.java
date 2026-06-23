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

package io.helidon.graphql.server;

import io.helidon.common.Api;

/**
 * GraphQL server context keys shared with WebServer GraphQL.
 */
@Api.Internal
public final class GraphQlContextKeys {
    /**
     * GraphQL Java parsed document key.
     */
    public static final String PARSED_DOCUMENT = GraphQlConstants.class.getName() + ".parsedDocument";

    private GraphQlContextKeys() {
    }
}
