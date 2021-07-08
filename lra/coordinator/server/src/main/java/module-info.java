/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/**
 * Mock LRA coordinator with Narayana like rest api for testing .
 */
module io.helidon.microprofile.lra.coordinator {
    requires java.logging;
    requires java.ws.rs;
    requires microprofile.lra.api;
    requires jakarta.enterprise.cdi.api;
    requires jakarta.inject.api;
    requires io.helidon.common.reactive;
    requires io.helidon.microprofile.scheduling;
    requires microprofile.config.api;
    requires io.helidon.webclient;
    requires io.helidon.microprofile.server;
}