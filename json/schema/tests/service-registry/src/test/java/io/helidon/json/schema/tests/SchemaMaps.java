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

package io.helidon.json.schema.tests;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import io.helidon.json.schema.JsonSchema;

@JsonSchema.Schema
record SchemaMaps(Map<String, String> values,
                  @JsonSchema.Required
                  @JsonSchema.Title("Counts")
                  @JsonSchema.Description("Named counts")
                  @JsonSchema.Object.MinProperties(1)
                  @JsonSchema.Object.MaxProperties(5)
                  @JsonSchema.Object.AdditionalProperties(true) Map<String, Integer> counts,
                  SortedMap<String, String> sortedValues,
                  @JsonSchema.Title("Labels") NamedValues namedValues) {

    @JsonSchema.Title("Named values")
    @JsonSchema.Object.MinProperties(1)
    static class NamedValues extends TreeMap<String, String> {
        public String label;
    }
}
