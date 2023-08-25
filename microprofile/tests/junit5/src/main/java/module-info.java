/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
 * JUnit5 extension module to run CDI tests.
 */
module io.helidon.microprofile.tests.junit5 {

    requires io.helidon.microprofile.cdi;
    requires io.helidon.config.mp;
    requires io.helidon.config.yaml.mp;
    requires org.junit.jupiter.api;
    requires jakarta.inject;
    requires transitive jakarta.cdi;
    requires transitive jakarta.ws.rs;

    exports io.helidon.microprofile.tests.junit5;

}
