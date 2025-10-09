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
package io.helidon.docs.se.json;

import io.helidon.json.schema.JsonSchema;
import io.helidon.json.schema.Schema;

class JsonSchemaSnippets {

    void jsonSchemaProgrammaticSnippet() {
        // tag::snippet_1[]
        Schema.builder()
                .rootObject(builder -> builder.description("Example JSON Schema")
                        .addIntegerProperty("exampleProperty", intBuilder -> intBuilder.minimum(0)))
                .build();
        // end::snippet_1[]
    }

    // tag::snippet_2[]
    @JsonSchema.Schema // <1>
    @JsonSchema.Description("Example JSON Schema")
    public record ExampleSchema(@JsonSchema.Integer.Minimum(0) int exampleProperty) {
    }
    // end::snippet_2[]

}
