/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.staticcontent;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import io.helidon.http.BadRequestException;
import io.helidon.http.DateTime;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.http.ServerRequest;

import org.hamcrest.collection.IsCollectionWithSize;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByteRangeRequestTest {
    private static final String FIRST_BYTE_RANGE = HeaderValues.create(HeaderNames.RANGE, "bytes=0-0").values();

    @Test
    void testFromUntilEnd() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=49-");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(1));
        ByteRangeRequest byteRange = requests.get(0);

        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(49L));
        assertThat(byteRange.length(), is(1L)); //(byte 49)
    }

    @Test
    void testFromUntil() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=49-49");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(1));
        ByteRangeRequest byteRange = requests.get(0);

        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(49L));
        assertThat(byteRange.length(), is(1L)); //(bytes 49 and 49)
    }

    @Test
    void testLast() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=-1");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(1));
        ByteRangeRequest byteRange = requests.get(0);

        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(49L));
        assertThat(byteRange.length(), is(1L)); //(bytes 49 and 49)
    }

    @Test
    void testSuffixRangeLongerThanRepresentation() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=-500");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(1));
        ByteRangeRequest byteRange = requests.getFirst();

        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(0L));
        assertThat(byteRange.length(), is(50L));
    }

    @Test
    void testSuffixPastFileLength() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=-51");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(1));
        ByteRangeRequest byteRange = requests.get(0);

        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(0L));
        assertThat(byteRange.length(), is(50L));
    }

    @Test
    void testSuffixNumberTooLarge() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=-9223372036854775808");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(1));
        ByteRangeRequest byteRange = requests.get(0);

        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(0L));
        assertThat(byteRange.length(), is(50L));
    }

    @Test
    void testWhitespaceAfterRangeUnit() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes= 0-0");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(1));
        ByteRangeRequest byteRange = requests.get(0);

        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(0L));
        assertThat(byteRange.length(), is(1L));
    }

    @Test
    void testRangeIgnoredForEmptyRepresentation() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=-1");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 0);

        assertThat(requests, IsCollectionWithSize.hasSize(0));
    }

    @Test
    void testExplicitEndPastFileLength() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=0-9223372036854775807");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 1);
        assertThat(requests, IsCollectionWithSize.hasSize(1));
        ByteRangeRequest byteRange = requests.get(0);

        assertThat(byteRange.fileLength(), is(1L));
        assertThat(byteRange.offset(), is(0L));
        assertThat(byteRange.length(), is(1L));
    }

    @Test
    void testExplicitEndNumberTooLarge() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=0-9223372036854775808");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(1));
        ByteRangeRequest byteRange = requests.get(0);

        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(0L));
        assertThat(byteRange.length(), is(50L));
    }

    @Test
    void testExplicitEndNumberTooLargeWithTrailingJunkRejected() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=1-9223372036854775808x");

        assertThrows(BadRequestException.class, () -> ByteRangeRequest.parse(header.values(), 50));
    }

    @Test
    void testEmptyRangeListElementsIgnored() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=,0-0,");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(1));
        ByteRangeRequest byteRange = requests.get(0);

        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(0L));
        assertThat(byteRange.length(), is(1L));
    }

    @Test
    void testInvalidRangeMemberRejectsHeader() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=0-0,5-4");

        assertThrows(BadRequestException.class, () -> ByteRangeRequest.parse(header.values(), 50));
    }

    @Test
    void testAmbiguousOverflowingRangeIgnored() {
        Header header = HeaderValues.create(HeaderNames.RANGE,
                                            "bytes=0-0,100000000000000000000-99999999999999999999");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 50);

        assertThat(requests, IsCollectionWithSize.hasSize(0));
    }

    @Test
    void testMaximumLongRangeIgnored() {
        Header header = HeaderValues.create(HeaderNames.RANGE,
                                            "bytes=9223372036854775807-9223372036854775807");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 50);

        assertThat(requests, IsCollectionWithSize.hasSize(0));
    }

    @Test
    void testLaterUnsatisfiableRangeDoesNotDiscardSatisfiableRange() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=0-0,9223372036854775808-");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(1));
        ByteRangeRequest byteRange = requests.get(0);

        assertThat(byteRange.fileLength(), is(50L));
        assertThat(byteRange.offset(), is(0L));
        assertThat(byteRange.length(), is(1L));
    }

    @Test
    void testFirstPositionNumberTooLarge() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=9223372036854775808-");

        HttpException exception = assertThrows(HttpException.class,
                                               () -> ByteRangeRequest.parse(header.values(), 50));
        assertThat(exception.status(), is(Status.REQUESTED_RANGE_NOT_SATISFIABLE_416));
        assertThat(exception.headers(), hasHeader(HeaderNames.CONTENT_RANGE, "bytes */50"));
    }

    @Test
    void testUnsupportedRangeUnitIgnored() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "items=0-0");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 50);
        assertThat(requests, IsCollectionWithSize.hasSize(0));
    }

    @Test
    void testMalformedLongRangeMemberRejectedBeforeValidMember() {
        Header header = HeaderValues.create(HeaderNames.RANGE,
                                            "bytes=" + "9".repeat(12_000) + ", 0-0");

        assertThrows(BadRequestException.class, () -> ByteRangeRequest.parse(header.values(), 50));
    }

    @Test
    void testMultiRangeMultiValue() {
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=-1", "bytes=47-48", "bytes=0-");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 50);
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
        Header header = HeaderValues.create(HeaderNames.RANGE, "bytes=-1, 47-48, 0-");

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(header.values(), 50);
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
    void ifRangeRequiresStrongExactEntityTag() {
        Instant modified = Instant.parse("2026-08-10T12:34:56Z");
        String entityTag = String.valueOf(modified.toEpochMilli());

        assertThat("Exact strong ETag should match",
                   ByteRangeRequest.parse(requestWithIfRange('"' + entityTag + '"'),
                                          FIRST_BYTE_RANGE,
                                          50,
                                          entityTag,
                                          false),
                   is(List.of(new ByteRangeRequest(50, 0, 1))));
        assertThat("Weak ETag should not match",
                   ByteRangeRequest.parse(requestWithIfRange("W/\"" + entityTag + '"'),
                                          FIRST_BYTE_RANGE,
                                          50,
                                          entityTag,
                                          false),
                   is(List.of()));
        assertThat("Bare ETag value should not match",
                   ByteRangeRequest.parse(requestWithIfRange(entityTag),
                                          FIRST_BYTE_RANGE,
                                          50,
                                          entityTag,
                                          false),
                   is(List.of()));
    }

    @Test
    void ifRangeRejectsDateValidators() {
        Instant modified = Instant.parse("2026-08-10T12:34:56.789Z");
        String entityTag = String.valueOf(modified.toEpochMilli());

        assertThat("Date validators should not match",
                   ByteRangeRequest.parse(requestWithIfRange(modified),
                                          FIRST_BYTE_RANGE,
                                          50,
                                          entityTag,
                                          false),
                   is(List.of()));
        assertThat("Different date should not match",
                   ByteRangeRequest.parse(requestWithIfRange(modified.plusSeconds(1)),
                                          FIRST_BYTE_RANGE,
                                          50,
                                          entityTag,
                                          false),
                   is(List.of()));
    }

    @Test
    void testIfRangeDateIsNotStrongValidator() {
        Instant lastModified = Instant.parse("2026-06-30T12:00:00.123456Z");
        ServerRequest req = requestWithIfRange(lastModified);

        List<ByteRangeRequest> requests = ByteRangeRequest.parse(req,
                                                                 FIRST_BYTE_RANGE,
                                                                 50,
                                                                 null,
                                                                 false);

        assertThat(requests, is(List.of()));
    }

    @Test
    void testIfRangeDateRequiresExactMatch() {
        Instant lastModified = Instant.parse("2026-06-30T12:00:00.123456Z");

        assertThat(ByteRangeRequest.parse(requestWithIfRange(lastModified.minusSeconds(1)),
                                          FIRST_BYTE_RANGE,
                                          50,
                                          null,
                                          false),
                   is(List.of()));
        assertThat(ByteRangeRequest.parse(requestWithIfRange(lastModified.plusSeconds(1)),
                                          FIRST_BYTE_RANGE,
                                          50,
                                          null,
                                          false),
                   is(List.of()));
    }

    @Test
    void testMultipleIfRangeValuesDoNotMatch() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.IF_RANGE, "\"tag\"");
        headers.add(HeaderNames.IF_RANGE, "\"other\"");
        ServerRequest req = Mockito.mock(ServerRequest.class);
        Mockito.when(req.headers()).thenReturn(ServerRequestHeaders.create(headers));

        assertThat(ByteRangeRequest.parse(req,
                                          FIRST_BYTE_RANGE,
                                          50,
                                          "tag",
                                          false),
                   is(List.of()));
    }

    @Test
    void testMalformedIfRangeEntityTagDoesNotMatch() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.IF_RANGE, "\"");
        ServerRequest req = Mockito.mock(ServerRequest.class);
        Mockito.when(req.headers()).thenReturn(ServerRequestHeaders.create(headers));

        assertThat(ByteRangeRequest.parse(req,
                                          FIRST_BYTE_RANGE,
                                          50,
                                          "123",
                                          false),
                   is(List.of()));
    }

    private static ServerRequest requestWithIfRange(Instant ifRange) {
        String value = ZonedDateTime.ofInstant(ifRange, ZoneOffset.UTC).format(DateTime.RFC_1123_DATE_TIME);
        return requestWithIfRange(value);
    }

    private static ServerRequest requestWithIfRange(String ifRange) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.IF_RANGE, ifRange);
        ServerRequest req = Mockito.mock(ServerRequest.class);
        Mockito.when(req.headers()).thenReturn(ServerRequestHeaders.create(headers));
        return req;
    }
}
