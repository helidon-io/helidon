/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

module io.helidon.webclient.tests.http3.modulepath.test {
    exports io.helidon.webclient.tests.http3.modulepath;

    requires io.helidon.webclient.api;
    requires io.helidon.webclient.http1;
    requires io.helidon.webclient.http3;

    requires hamcrest.all;
    requires org.junit.jupiter.api;

    opens io.helidon.webclient.tests.http3.modulepath to org.junit.platform.commons;
}
