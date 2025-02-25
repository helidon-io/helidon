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

package io.helidon.webclient.tests;

import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class Send100ContinueTest {
    private static final byte[] DATA = new byte[] { 1, 2, 3 };

    private final WebClient webClient;
    private final Http1Client http1Client;

    Send100ContinueTest(WebClient webClient, Http1Client http1Client) {
        this.webClient = webClient;
        this.http1Client = http1Client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.post("/100Continue", (req, res) -> {
                    res.status(req.headers().contains(HeaderValues.EXPECT_100) ?
                                       Status.OK_200 : Status.BAD_REQUEST_400).send();
                })
                .post("/no100Continue", (req, res) -> {
                    res.status(req.headers().contains(HeaderValues.EXPECT_100) ?
                                       Status.BAD_REQUEST_400 : Status.OK_200).send();
                });
    }

    @Test
    public void test100ContinueDefaultWeb() {
        try (HttpClientResponse response = webClient.post("/100Continue")
                .outputStream(os -> { os.write(DATA); os.close(); })) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    @Test
    public void no100ContinueWeb() {
        try (HttpClientResponse response = webClient.post("/no100Continue")
                .sendExpectContinue(false)      // turns off 100 continue
                .outputStream(os -> { os.write(DATA); os.close(); })) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    @Test
    public void test100ContinueDefaultHttp1() {
        try (HttpClientResponse response = http1Client.post("/100Continue")
                .outputStream(os -> { os.write(DATA); os.close(); })) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    @Test
    public void no100ContinueHttp1() {
        try (HttpClientResponse response = http1Client.post("/no100Continue")
                .sendExpectContinue(false)      // turns off 100 continue
                .outputStream(os -> { os.write(DATA); os.close(); })) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }
}
