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

package io.helidon.metrics;

import io.helidon.common.http.Http;
import io.helidon.webserver.RequestHeaders;
import io.helidon.webserver.WebServerHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit test for {@link MetricsSupport}.
 *
 * The metrics data generated depends on the Accept header specified by
 * the client. If the client prefers JSON data, then we send JSON data.
 * If the client prefers text/plain, then we send Prometheus data. If
 * the client doesn't care and accepts JSON data, then we send JSON.
 * If the client doesn't accept JSON data, we send Prometheus text.
 */
class AcceptHeaderTest {

    private RequestHeaders constructRequestHeaders(String name, String... values) {
        Map<String, List<String>> map = new HashMap<>(1);
        map.put(name, new ArrayList<>(Arrays.asList(values)));
        return WebServerHelper.constructRequestHeaders(map);
    }

    @Test
    void testPrometheusAccept() {
        RequestHeaders hs = constructRequestHeaders(Http.Header.ACCEPT, "text/plain;version=0.0.4;q=1,*/*;q=0.1");
        assertFalse(MetricsSupport.requestsJsonData(hs));
    }

    @Test
    void testTextPlainAccept() {
        RequestHeaders hs = constructRequestHeaders(Http.Header.ACCEPT, "text/plain");
        assertFalse(MetricsSupport.requestsJsonData(hs));
    }

    @Test
    void testTextPlain2Accept() {
        RequestHeaders hs = constructRequestHeaders(Http.Header.ACCEPT, "text/plain;q=1,application/json;q=.5");
        assertFalse(MetricsSupport.requestsJsonData(hs));
    }

    @Test
    void testTextPlain3Accept() {
        RequestHeaders hs = constructRequestHeaders(Http.Header.ACCEPT, "application/xml");
        assertFalse(MetricsSupport.requestsJsonData(hs));
    }

    @Test
    void testJsonAccept() {
        RequestHeaders hs = constructRequestHeaders(Http.Header.ACCEPT, "application/json;q=1,*/*;q=0.1");
        assertTrue(MetricsSupport.requestsJsonData(hs));
    }

    @Test
    void testJson2Accept() {
        RequestHeaders hs = constructRequestHeaders(Http.Header.ACCEPT, "application/json;q=1,text/plain;q=0.5");
        assertTrue(MetricsSupport.requestsJsonData(hs));
    }

    @Test
    void testWildCardAccept() {
        RequestHeaders hs = constructRequestHeaders(Http.Header.ACCEPT, "*/*");
        assertTrue(MetricsSupport.requestsJsonData(hs));
    }

    @Test
    void testNoAccept() {
        // No Accept header
        RequestHeaders hs = constructRequestHeaders(Http.Header.USER_AGENT, "foo");
        assertTrue(MetricsSupport.requestsJsonData(hs));
    }
}
