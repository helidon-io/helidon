/*
 * Copyright (c) 2017, 2024 Oracle and/or its affiliates.
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

import io.helidon.config.etcd.EtcdWatcherProvider;

/**
 * Etcd config source implementation.
 */
module io.helidon.config.etcd {

    requires com.google.common;
    requires com.google.protobuf;
    requires etcd4j;
    requires io.grpc;
    requires io.grpc.protobuf;
    requires io.grpc.stub;
    requires io.helidon.common.media.type;
    requires io.helidon.common;


    requires static java.annotation;

    requires transitive io.helidon.config;

    exports io.helidon.config.etcd;

    provides io.helidon.config.spi.ConfigSourceProvider with io.helidon.config.etcd.EtcdConfigSourceProvider;
    provides io.helidon.config.spi.ChangeWatcherProvider with EtcdWatcherProvider;

}
