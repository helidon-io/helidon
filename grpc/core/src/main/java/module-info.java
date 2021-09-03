/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import io.helidon.grpc.core.JavaMarshaller;
import io.helidon.grpc.core.JsonbMarshaller;
import io.helidon.grpc.core.MarshallerSupplier;

/**
 * gRPC Core Module.
 */
module io.helidon.grpc.core {
    exports io.helidon.grpc.core;

    requires transitive io.helidon.config;
    requires transitive io.helidon.config.objectmapping;
    requires transitive io.helidon.common.configurable;
    requires transitive io.helidon.common;
    requires io.helidon.common.context;
    requires io.helidon.common.http;

    requires grpc.netty;
    requires transitive grpc.protobuf;
    requires grpc.protobuf.lite;
    requires transitive grpc.stub;
    requires transitive io.grpc;
    requires io.netty.handler;
    requires io.netty.transport;
    requires transitive com.google.protobuf;

    requires java.annotation;
    requires static java.json.bind;
    requires java.logging;
    requires java.naming;

    requires jakarta.inject.api;

    provides MarshallerSupplier with
            MarshallerSupplier.DefaultMarshallerSupplier,
            MarshallerSupplier.ProtoMarshallerSupplier,
            JavaMarshaller.Supplier,
            JsonbMarshaller.Supplier;
}
