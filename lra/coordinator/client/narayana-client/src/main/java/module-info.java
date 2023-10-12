/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

/**
 * Coordinator client for coordinators using Narayana like API.
 */
module io.helidon.lra.coordinator.client.narayana {

    requires io.helidon.faulttolerance;
    requires io.helidon.webclient;
    requires microprofile.config.api;
    requires microprofile.lra.api;

    requires transitive io.helidon.lra.coordinator.client;
    requires transitive io.helidon.microprofile.config;

    provides io.helidon.lra.coordinator.client.CoordinatorClient
            with io.helidon.lra.coordinator.client.narayana.NarayanaClient;

}