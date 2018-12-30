/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
 * Aggregator module for microprofile 1.2.
 */
module io.helidon.microprofile.v1_2 {
    requires transitive io.helidon.mp.config.cdi;
    requires transitive io.helidon.mp.config;
    requires transitive io.helidon.mp.server;
    requires transitive io.helidon.mp.health;
    requires transitive io.helidon.mp.metrics;
    requires transitive io.helidon.mp.faulttolerance;
    requires transitive io.helidon.mp.jwt.auth.cdi;

    requires io.helidon.mp.health.checks;
}
