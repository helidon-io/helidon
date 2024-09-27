/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;

import org.junit.jupiter.api.Test;

/**
 * Tests WebClientMockTest.
 */
class WebClientMockTest {

    @Test
    public void useMock() {
        WebClient webClient = mockWebClient(Status.OK_200);
        HttpClientResponse response = webClient.post("http://test.com")
                .header(HeaderNames.AUTHORIZATION, "Bearer asdsadsad")
                .submit(new Object());
        assertEquals(Status.OK_200, response.status());
    }

    private WebClient mockWebClient(Status status) {
        WebClient webClient = mock(WebClient.class);
        HttpClientRequest request = mock(HttpClientRequest.class);
        HttpClientResponse response = mock(HttpClientResponse.class);
        doReturn(request).when(webClient).post(anyString());
        doReturn(request).when(request).header(any(HeaderName.class), anyString());
        doReturn(response).when(request).submit(any(Object.class));
        when(response.status()).thenReturn(status);
        return webClient;
    }

}
