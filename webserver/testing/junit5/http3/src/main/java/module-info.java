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

/**
 * Helidon WebServer Testing JUnit 5 Support for HTTP/3.
 */
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.webserver.testing.junit5.http3 {
    requires io.helidon.common;
    requires io.helidon.http.http3;
    requires io.helidon.webclient.http3;
    requires io.helidon.webserver.http3;

    requires transitive io.helidon.webserver.testing.junit5;

    exports io.helidon.webserver.testing.junit5.http3;

    provides io.helidon.webserver.testing.junit5.spi.ServerJunitExtension
            with io.helidon.webserver.testing.junit5.http3.Http3ServerExtension;

}
