/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject.tests.configbeans;

import java.util.Random;

import io.helidon.builder.api.Prototype;

/**
 * aka SSLContextBuilder.
 * Note that this is just a normal builder, and will not be integrated with Config.
 * Builder for configuring a new SslContext for creation.
 */
@Prototype.Blueprint
interface SSLContextConfigBlueprint {

    String PROTOCOL = "TLS";
    Random RANDOM = new Random();

    FakeKeyConfig privateKeyConfig();

    FakeKeyConfig trustConfig();

    long sessionCacheSize();

    long sessionTimeout();

}
