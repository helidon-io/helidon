/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.tracing;

import io.helidon.common.types.TypeName;

final class TracingTypes {
    static final TypeName ANNOTATION_TRACED = TypeName.create("io.helidon.tracing.Tracing.Traced");
    static final TypeName ANNOTATION_TAG_PARAM = TypeName.create("io.helidon.tracing.Tracing.ParamTag");

    static final TypeName TRACER = TypeName.create("io.helidon.tracing.Tracer");
    static final TypeName SPAN_KIND = TypeName.create("io.helidon.tracing.Span.Kind");

    private TracingTypes() {
    }
}
