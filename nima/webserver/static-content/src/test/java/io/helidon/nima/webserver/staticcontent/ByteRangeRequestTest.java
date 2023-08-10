/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.staticcontent;

import java.util.List;

import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderNames;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import org.hamcrest.collection.IsCollectionWithSize;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ByteRangeRequestTest {
    @Test
    void testFromUntilEnd() {
        HeaderValue header = Http.HeaderNames.create(HeaderNames.RANGE, "bytes=49-");
        ServerRequest req = Mockito.mock(ServerRequest.class);
        ServerResponse res = Mockito.mock(ServerResponse.class);

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(req, res, header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(1));
        ByteRangeRequest byteRange = requests.get(0);

        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(49L));
        assertThat(byteRange.length(), is(1L)); //(byte 49)
    }

    @Test
    void testFromUntil() {
        HeaderValue header = HeaderNames.create(Http.HeaderNames.RANGE, "bytes=49-49");
        ServerRequest req = Mockito.mock(ServerRequest.class);
        ServerResponse res = Mockito.mock(ServerResponse.class);

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(req, res, header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(1));
        ByteRangeRequest byteRange = requests.get(0);

        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(49L));
        assertThat(byteRange.length(), is(1L)); //(bytes 49 and 49)
    }

    @Test
    void testLast() {
        HeaderValue header = Http.HeaderNames.create(Http.HeaderNames.RANGE, "bytes=-1");
        ServerRequest req = Mockito.mock(ServerRequest.class);
        ServerResponse res = Mockito.mock(ServerResponse.class);

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(req, res, header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(1));
        ByteRangeRequest byteRange = requests.get(0);

        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(49L));
        assertThat(byteRange.length(), is(1L)); //(bytes 49 and 49)
    }

    @Test
    void testMultiRangeMultiValue() {
        HeaderValue header = Http.HeaderNames.create(Http.HeaderNames.RANGE, "bytes=-1", "bytes=47-48", "bytes=0-");
        ServerRequest req = Mockito.mock(ServerRequest.class);
        ServerResponse res = Mockito.mock(ServerResponse.class);

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(req, res, header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(3));

        ByteRangeRequest byteRange = requests.get(0);
        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(49L));
        assertThat(byteRange.length(), is(1L)); //(bytes 49 and 49)

        byteRange = requests.get(1);
        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(47L));
        assertThat(byteRange.length(), is(2L)); //(bytes 47 and 48)

        byteRange = requests.get(2);
        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(0L));
        assertThat(byteRange.length(), is(50L)); //(bytes 0 to 49)
    }

    @Test
    void testMultiRangeSingleValue() {
        HeaderValue header = HeaderNames.create(Http.HeaderNames.RANGE, "bytes=-1, bytes=47-48, s bytes=0-");
        ServerRequest req = Mockito.mock(ServerRequest.class);
        ServerResponse res = Mockito.mock(ServerResponse.class);

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(req, res, header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(3));

        ByteRangeRequest byteRange = requests.get(0);
        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(49L));
        assertThat(byteRange.length(), is(1L)); //(bytes 49 and 49)

        byteRange = requests.get(1);
        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(47L));
        assertThat(byteRange.length(), is(2L)); //(bytes 47 and 48)

        byteRange = requests.get(2);
        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(0L));
        assertThat(byteRange.length(), is(50L)); //(bytes 0 to 49)
    }
}