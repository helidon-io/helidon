/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 * TestNG extension module to run CDI tests.
 */
module io.helidon.microprofile.tests.testng {
    requires io.helidon.microprofile.cdi;
    requires io.helidon.config.mp;
    requires io.helidon.config.yaml.mp;
    requires org.testng;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.ws.rs;
    requires microprofile.config.api;

    exports io.helidon.microprofile.tests.testng;

    provides org.testng.ITestNGListener with io.helidon.microprofile.tests.testng.HelidonTestNGListener;
}