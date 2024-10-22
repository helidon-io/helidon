/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import io.helidon.microprofile.testing.testng.HelidonTestNgListener;

/**
 * TestNG extension module to run CDI tests.
 */
module io.helidon.microprofile.testing.testng {

    requires io.helidon.config.mp;
    requires io.helidon.config.yaml.mp;
    requires io.helidon.microprofile.cdi;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.ws.rs;
    requires microprofile.config.api;
    requires org.testng;

    requires static io.helidon.microprofile.server;
    requires static jersey.cdi1x;
    requires static jersey.weld2.se;

    exports io.helidon.microprofile.testing.testng;

    provides org.testng.ITestNGListener with HelidonTestNgListener;

}