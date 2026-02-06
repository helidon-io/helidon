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

final class TimeProto {
    private TimeProto() {
    }
    public static void registerAllExtensions(
            com.google.protobuf.ExtensionRegistryLite registry) {
    }

    public static void registerAllExtensions(
            com.google.protobuf.ExtensionRegistry registry) {
        registerAllExtensions(
                (com.google.protobuf.ExtensionRegistryLite) registry);
    }
    static final com.google.protobuf.Descriptors.Descriptor
            TIME_DESCRIPTOR;
    static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
            FIELD_ACCESSOR_TABLE;

    public static com.google.protobuf.Descriptors.FileDescriptor
    getDescriptor() {
        return descriptor;
    }
    private static  com.google.protobuf.Descriptors.FileDescriptor
            descriptor;

    static {
        String[] descriptorData = {
                "\n\030jaeger/api_v2/time.proto\022\031io.opentelem"
                        + "etry.internal\"&\n\004Time\022\017\n\007seconds\030\001 \001(\003\022\r"
                        + "\n\005nanos\030\002 \001(\005BA\n2io.opentelemetry.export"
                        + "er.jaeger.internal.protobufB\tTimeProtoP\001"
                        + "b\006proto3"
        };
        descriptor = com.google.protobuf.Descriptors.FileDescriptor
                .internalBuildGeneratedFileFrom(descriptorData,
                                                new com.google.protobuf.Descriptors.FileDescriptor[] {
                                                });
        TIME_DESCRIPTOR =
                getDescriptor().getMessageTypes().get(0);
        FIELD_ACCESSOR_TABLE = new
                com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
                TIME_DESCRIPTOR,
                new String[] {"Seconds", "Nanos", });
    }

    // @@protoc_insertion_point(outer_class_scope)
}
