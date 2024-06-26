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

package io.helidon.ai.openai;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.util.List;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.HttpClient;
import io.helidon.webclient.api.HttpClientResponse;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OpenAIChatModelTest {

    @Test
    void testOK() {
        HttpClient<?> httpClient = mockHttpClient(Status.OK_200);
        OpenAIChatModel chatModel = OpenAIChatModel.create("api-key", httpClient);
        OpenAIMessage message = new OpenAIMessage();
        message.setContent("Say hello");
        OpenAIRequest req = new OpenAIRequest();
        req.setMessages(List.of(message));
        chatModel.call(new OpenAIRequest());
    }

    @Test
    void testNOK() {
        HttpClient<?> httpClient = mockHttpClient(Status.UNAUTHORIZED_401);
        OpenAIChatModel chatModel = OpenAIChatModel.create("api-key", httpClient);
        OpenAIMessage message = new OpenAIMessage();
        message.setContent("Say hello");
        OpenAIRequest req = new OpenAIRequest();
        req.setMessages(List.of(message));
        assertThrows(IllegalStateException.class, () -> chatModel.call(new OpenAIRequest()));
    }

    private HttpClient<?> mockHttpClient(Status status) {
        HttpClient<?> httpClient = Mockito.mock(HttpClient.class);
        ClientRequest<?> request = Mockito.mock(ClientRequest.class);
        HttpClientResponse response = Mockito.mock(HttpClientResponse.class);
        doReturn(request).when(httpClient).post(anyString());
        doReturn(request).when(request).header(eq(HeaderNames.CONTENT_TYPE), anyString());
        doReturn(request).when(request).header(eq(HeaderNames.AUTHORIZATION), anyString());
        doReturn(response).when(request).submit(any(OpenAIRequest.class));
        when(response.status()).thenReturn(status);
        return httpClient;
    }
}
