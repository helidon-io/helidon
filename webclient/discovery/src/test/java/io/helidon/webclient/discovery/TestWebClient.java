/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.webclient.discovery;

import io.helidon.webclient.api.WebClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TestWebClient {

    private TestWebClient() {
        super();
    }

    @Test
    void showThatWebClientRequiresLowercaseHttpAndHttpsSchemesOnly() {
        WebClient c = WebClient.builder().build();
        assertThrows(IllegalArgumentException.class, c.get("HTTP://example.com")::request); // !
        assertThrows(IllegalArgumentException.class, c.get("htTP://example.com")::request); // !
        assertThrows(IllegalArgumentException.class, c.get("HTTPS://example.com")::request); // !
        assertThrows(IllegalArgumentException.class, c.get("httPS://example.com")::request); // !
        assertThrows(IllegalArgumentException.class, c.get("otherscheme://example.com")::request); // !
    }

}
