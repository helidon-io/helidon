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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.nima.webserver.SimpleHandler;
import io.helidon.nima.webserver.http.HttpException;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

record ByteRangeRequest(long fileLength, long offset, long length) {
    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d+)?-(\\d+)?(?:, )?");

    static List<ByteRangeRequest> parse(ServerRequest req, ServerResponse res, String headerValues, long fileLength) {
        Matcher matcher = RANGE_PATTERN.matcher(headerValues);

        List<ByteRangeRequest> parts = new ArrayList<>();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            //"bytes=0-1023" - 0 to 1023 (included both)
            // 500- (= 500 until end)
            // -500 (= last 500)
            // 0-0,-1 (first and last)
            // a-b, b-c (multipart)
            String firstGroup = matcher.group(1);
            String secondGroup = matcher.group(2);
            long from = 0;
            long last = fileLength - 1;
            if (firstGroup != null) {
                from = Long.parseLong(firstGroup);
            }
            if (secondGroup != null) {
                long second = Long.parseLong(secondGroup);
                if (firstGroup == null) {
                    from = fileLength - second;
                    last = fileLength - 1;
                } else {
                    last = second;
                }
            }
            parts.add(ByteRangeRequest.create(req, res, from, last, fileLength));
        }
        if (!found) {
            throw HttpException.builder()
                    .request(req)
                    .response(res)
                    .type(SimpleHandler.EventType.BAD_REQUEST)
                    .message("Invalid range header")
                    .build();
        }

        return parts;
    }

    void setContentRange(ServerResponse response) {
        // status: 206 Partial Content
        // Content-Range: bytes 0-1023/146515
        // Content-Length: 1024
        long last = (offset + length) - 1;
        response.header(Header.CONTENT_RANGE.withValue(true,
                                                       false,
                                                       "bytes " + offset + "-" + last + "/" + fileLength));
        response.header(Header.CONTENT_LENGTH.withValue(true, false, String.valueOf(length)));
        response.status(Http.Status.PARTIAL_CONTENT_206);
    }

    private static ByteRangeRequest create(ServerRequest req, ServerResponse res, long offset, long last, long fileLength) {
        if (offset >= fileLength || last < offset) {
            res.header(Header.CONTENT_RANGE.withValue("*/" + fileLength));
            throw HttpException.builder()
                    .request(req)
                    .response(res)
                    .type(SimpleHandler.EventType.BAD_REQUEST)
                    .status(Http.Status.REQUESTED_RANGE_NOT_SATISFIABLE_416)
                    .build();
        }

        long length = (last - offset) + 1;

        return new ByteRangeRequest(fileLength, offset, length);
    }
}
