/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.sse;

import java.util.List;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

@ServerTest
class SseCompressionTest extends SseBaseTest {

    private static final List<String> GZIP_HEADER = List.of("Accept-Encoding: gzip");
    private static final List<String> DEFLATE_HEADER = List.of("Accept-Encoding: deflate");

    SseCompressionTest(WebServer webServer) {
        super(webServer);
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/sseString1", SseCompressionTest::sseString1);
        rules.get("/sseJson1", SseCompressionTest::sseJson1);
        rules.get("/sseMixed", SseCompressionTest::sseMixed);
        rules.get("/sseIdComment", SseCompressionTest::sseIdComment);
    }

    @Test
    void testSseString1() throws Exception {
        testSse("/sseString1", GZIP_HEADER, "data:hello", "data:world");
    }

    @Test
    void testSseJson1() throws Exception {
        testSse("/sseJson1", DEFLATE_HEADER, "data:{\"hello\":\"world\"}");
    }

    @Test
    void testSseMixed() throws Exception {
        testSse("/sseMixed", GZIP_HEADER, "data:hello", "data:world",
                "data:{\"hello\":\"world\"}", "data:{\"hello\":\"world\"}");
    }

    @Test
    void testIdComment() throws Exception {
        testSse("/sseIdComment", DEFLATE_HEADER, ":This is a comment\nid:1\ndata:hello");
    }
}
