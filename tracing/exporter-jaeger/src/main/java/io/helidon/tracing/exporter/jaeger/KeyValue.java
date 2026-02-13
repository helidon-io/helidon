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

final class KeyValue {
    public static final ProtoFieldInfo KEY = ProtoFieldInfo.create(1, 10, "key");

    public static final ProtoFieldInfo V_TYPE = ProtoFieldInfo.create(2, 16, "vType");

    public static final ProtoFieldInfo V_STR = ProtoFieldInfo.create(3, 26, "vStr");

    public static final ProtoFieldInfo V_BOOL = ProtoFieldInfo.create(4, 32, "vBool");

    public static final ProtoFieldInfo V_INT64 = ProtoFieldInfo.create(5, 40, "vInt64");

    public static final ProtoFieldInfo V_FLOAT64 = ProtoFieldInfo.create(6, 49, "vFloat64");

    public static final ProtoFieldInfo V_BINARY = ProtoFieldInfo.create(7, 58, "vBinary");

    private KeyValue() {
    }

}
