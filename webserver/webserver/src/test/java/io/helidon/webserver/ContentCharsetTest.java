/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.Map;

import io.helidon.common.CollectionsHelper;

import org.junit.jupiter.api.Test;

import static io.helidon.common.CollectionsHelper.listOf;
import static io.helidon.common.CollectionsHelper.mapOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * The StringContentReaderTest.
 */
public class ContentCharsetTest {
    @Test
    public void requestContentCharset() {
        RequestTestStub request = charset(mapOf("content-type", listOf("application/json; charset=cp1250")));

        assertThat(Request.contentCharset(request), is(Charset.forName("cp1250")));
    }

    @Test
    public void invalidRequestContentCharset() {
        RequestTestStub request = charset(mapOf("content-type", listOf("application/json; charset=invalid-charset-name")));

        assertThrows(UnsupportedCharsetException.class, () -> Request.contentCharset(request));
    }

    @Test
    public void nonexistentCharset() {
        RequestTestStub request = charset(mapOf("content-type", listOf("application/json")));

        assertThat(Request.contentCharset(request), is(Request.DEFAULT_CHARSET));
    }

    @Test
    public void missingContentType() {
        RequestTestStub request = charset(CollectionsHelper.mapOf());

        assertThat(Request.contentCharset(request), is(Request.DEFAULT_CHARSET));
    }

    private RequestTestStub charset(Map<String, List<String>> map) {
        BareRequest bareRequestMock = mock(BareRequest.class);
        doReturn(URI.create("http://0.0.0.0:1234")).when(bareRequestMock).uri();
        doReturn(map).when(bareRequestMock).headers();

        return new RequestTestStub(bareRequestMock, mock(WebServer.class));
    }
}
