/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.ReactiveStreamsAdapter;
import io.helidon.webserver.spi.BareRequest;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static io.helidon.common.CollectionsHelper.listOf;
import static io.helidon.common.CollectionsHelper.mapOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * The StringContentReaderTest.
 */
public class StringContentReaderTest {

    @Test
    public void invalidCharsetTest() throws Exception {
        Flux<DataChunk> flux = Flux.just("2010-01-02").map(s -> DataChunk.create(s.getBytes()));

        CompletableFuture<? extends String> future =
                new StringContentReader("invalid-charset-name")
                        .apply(ReactiveStreamsAdapter.publisherToFlow(flux))
                        .toCompletableFuture();

        try {
            future.get(10, TimeUnit.SECONDS);

            fail("Should have failed due to an invalid charset");
        } catch (ExecutionException e) {

            assertThat(e.getCause(), allOf(
                    instanceOf(IllegalArgumentException.class),
                    hasProperty("message", containsString("Cannot produce a string with the expected charset."))));

        } catch (InterruptedException | TimeoutException e) {
            throw e;
        }

        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    public void charsetTest() throws Exception {
        Flux<DataChunk> flux = Flux.just(DataChunk.create(new byte[] {(byte) 225, (byte) 226, (byte) 227}));

        CompletableFuture<? extends String> future =
                new StringContentReader("cp1250")
                        .apply(ReactiveStreamsAdapter.publisherToFlow(flux))
                        .toCompletableFuture();

        String s = future.get(10, TimeUnit.SECONDS);

        assertThat(s, Is.is("áâă"));
    }

    @Test
    public void requestContentCharset() throws Exception {
        RequestTestStub request = charset(mapOf("content-type", listOf("application/json; charset=cp1250")));

        assertEquals("cp1250", StringContentReader.requestContentCharset(request));
    }

    @Test
    public void invalidRequestContentCharset() throws Exception {
        RequestTestStub request = charset(mapOf("content-type", listOf("application/json; charset=invalid-charset-name")));

        assertEquals("invalid-charset-name", StringContentReader.requestContentCharset(request));
    }

    @Test
    public void nonexistentCharset() throws Exception {
        RequestTestStub request = charset(mapOf("content-type", listOf("application/json")));

        assertEquals(StringContentReader.DEFAULT_CHARSET, StringContentReader.requestContentCharset(request));
    }

    @Test
    public void missingContentType() throws Exception {
        RequestTestStub request = charset(CollectionsHelper.mapOf());

        assertEquals(StringContentReader.DEFAULT_CHARSET, StringContentReader.requestContentCharset(request));
    }

    private RequestTestStub charset(Map<String, List<String>> map) {
        BareRequest bareRequestMock = mock(BareRequest.class);
        doReturn(URI.create("http://0.0.0.0:1234")).when(bareRequestMock).getUri();
        doReturn(map).when(bareRequestMock).getHeaders();

        return new RequestTestStub(bareRequestMock, mock(WebServer.class));
    }
}
