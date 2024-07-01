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
package io.helidon.webclient.api;

import java.net.URI;
import java.util.Collections;
import java.util.Optional;

import io.helidon.common.socket.SocketOptions;
import io.helidon.http.Method;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class HttpClientRequestTest {

    @Test
    void testCacheSharing() {
        HttpClientRequest req1 = mockHttpClientRequest();
        HttpClientRequest req2 = mockHttpClientRequest();
        assertThat(req1.clientSpiLruCache(), is(sameInstance(req2.clientSpiLruCache())));
    }

    private HttpClientRequest mockHttpClientRequest() {
        WebClient webClient = Mockito.mock(WebClient.class);
        WebClientConfig webClientConfig = Mockito.mock(WebClientConfig.class);
        Mockito.when(webClientConfig.readTimeout()).thenReturn(Optional.empty());
        Mockito.when(webClientConfig.socketOptions()).thenReturn(Mockito.mock(SocketOptions.class));
        ClientUri clientUri = ClientUri.create(URI.create("http://localhost"));
        return new HttpClientRequest(webClient,
                                     webClientConfig,
                                     Method.GET,
                                     clientUri,
                                     Collections.emptyMap(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList());
    }
}
