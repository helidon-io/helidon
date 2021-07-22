/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
 * A simple HTTP like client suitable for tests of {@code Web Server} {@link io.helidon.webserver.Routing Routing}.
 *
 * @see io.helidon.webserver.testsupport.TestClient
 */
module io.helidon.webserver.test.support {
    requires io.helidon.webserver;
    requires io.helidon.common.http;

    requires java.logging;
    requires org.junit.jupiter.api;
}
