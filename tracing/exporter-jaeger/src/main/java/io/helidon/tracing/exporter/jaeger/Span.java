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

import io.opentelemetry.exporter.internal.marshal.ProtoFieldInfo;

final class Span {
    public static final ProtoFieldInfo TRACE_ID = ProtoFieldInfo.create(1, 10, "traceId");

    public static final ProtoFieldInfo SPAN_ID = ProtoFieldInfo.create(2, 18, "spanId");

    public static final ProtoFieldInfo OPERATION_NAME = ProtoFieldInfo.create(3, 26, "operationName");

    public static final ProtoFieldInfo REFERENCES = ProtoFieldInfo.create(4, 34, "references");

    public static final ProtoFieldInfo FLAGS = ProtoFieldInfo.create(5, 40, "flags");

    public static final ProtoFieldInfo START_TIME = ProtoFieldInfo.create(6, 50, "startTime");

    public static final ProtoFieldInfo DURATION = ProtoFieldInfo.create(7, 58, "duration");

    public static final ProtoFieldInfo TAGS = ProtoFieldInfo.create(8, 66, "tags");

    public static final ProtoFieldInfo LOGS = ProtoFieldInfo.create(9, 74, "logs");

    public static final ProtoFieldInfo PROCESS = ProtoFieldInfo.create(10, 82, "process");

    public static final ProtoFieldInfo PROCESS_ID = ProtoFieldInfo.create(11, 90, "processId");

    public static final ProtoFieldInfo WARNINGS = ProtoFieldInfo.create(12, 98, "warnings");

    private Span() {
    }
}
