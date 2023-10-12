/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
 * Integration test for routing composition.
 */
module helidon.tests.integration.webserver.upgrade {

    requires io.helidon.logging.common;
    requires io.helidon.http;
    requires io.helidon.webserver;
    requires io.helidon.common.pki;
    requires io.helidon.webserver.http2;
    requires io.helidon.webserver.websocket;
	
	exports io.helidon.webserver.tests.upgrade;
	
}