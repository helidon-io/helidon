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

package io.helidon.tracing.exporter.jaeger;

import io.opentelemetry.exporter.internal.marshal.ProtoEnumInfo;

final class ValueType {
    public static final ProtoEnumInfo STRING = ProtoEnumInfo.create(0, "STRING");

    public static final ProtoEnumInfo BOOL = ProtoEnumInfo.create(1, "BOOL");

    public static final ProtoEnumInfo INT64 = ProtoEnumInfo.create(2, "INT64");

    public static final ProtoEnumInfo FLOAT64 = ProtoEnumInfo.create(3, "FLOAT64");

    public static final ProtoEnumInfo BINARY = ProtoEnumInfo.create(4, "BINARY");

    private ValueType() {
    }
}
