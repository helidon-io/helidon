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
 * Microprofile configuration module.
 */
module io.helidon.metrics {
    requires java.logging;

    requires io.helidon.common;
    requires transitive microprofile.metrics.api;
    requires java.management;
    requires io.helidon.webserver;
    requires io.helidon.webserver.json;
    requires org.glassfish.java.json;

    exports io.helidon.metrics;
}
