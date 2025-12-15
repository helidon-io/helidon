/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j.providers.gemini;

import java.util.function.Supplier;

import io.helidon.service.registry.Service;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Service.Singleton
public class MockHttpClientProvider implements Supplier<HttpClientBuilder> {

    static final String MOCK_RESPONSE = "Mock response";

    @Override
    public HttpClientBuilder get() {
        var builderMock = Mockito.mock(HttpClientBuilder.class);
        var clientMock = Mockito.mock(HttpClient.class);
        var responseMock = Mockito.mock(SuccessfulHttpResponse.class);

        when(builderMock.build()).thenReturn(clientMock);
        when(builderMock.connectTimeout(any())).thenReturn(builderMock);
        when(builderMock.readTimeout(any())).thenReturn(builderMock);
        when(clientMock.execute(any())).thenReturn(responseMock);
        when(responseMock.body())
                .thenReturn("""
                                    {
                                      "responseId": "65c2f9e0-4b1a-11ee-bf8a-0242ac120002",
                                      "modelVersion": "gemini-1.5-pro-001",
                                      "candidates": [
                                        {
                                          "content": {
                                            "parts": [
                                              {
                                                "text": "%MOCK_RESPONSE%"
                                              }
                                            ]
                                          },
                                          "finishReason": "STOP"
                                        }
                                      ],
                                      "usageMetadata": {
                                            "promptTokenCount": 21,
                                            "candidatesTokenCount": 7,
                                            "totalTokenCount": 28
                                          }
                                    }
                                    """.replaceAll("%MOCK_RESPONSE%", MOCK_RESPONSE));
        return builderMock;
    }
}
