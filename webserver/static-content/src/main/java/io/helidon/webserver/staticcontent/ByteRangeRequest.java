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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.http.BadRequestException;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerRequest;

record ByteRangeRequest(long fileLength, long offset, long length) {
    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d+)?-(\\d+)?(?:, )?");

    static List<ByteRangeRequest> parse(String headerValues, long fileLength) {
        return parseRanges(headerValues, fileLength);
    }

    static List<ByteRangeRequest> parse(ServerRequest req,
                                        String headerValues,
                                        long fileLength,
                                        String etag,
                                        boolean weakEtag) {
        if (!ifRangeMatches(req, etag, weakEtag)) {
            return List.of();
        }

        return parseRanges(headerValues, fileLength);
    }

    Header contentRangeHeader() {
        long last = (offset + length) - 1;
        return HeaderValues.create(HeaderNames.CONTENT_RANGE,
                                   true,
                                   false,
                                   "bytes " + offset + "-" + last + "/" + fileLength);
    }

    private static List<ByteRangeRequest> parseRanges(String headerValues,
                                                      long fileLength) {
        Matcher matcher = RANGE_PATTERN.matcher(headerValues);

        List<ByteRangeRequest> parts = new ArrayList<>();
        boolean found = false;
        boolean satisfiableEmptyRange = false;
        while (matcher.find()) {
            found = true;
            //"bytes=0-1023" - 0 to 1023 (included both)
            // 500- (= 500 until end)
            // -500 (= last 500)
            // 0-0,-1 (first and last)
            // a-b, b-c (multipart)
            String firstGroup = matcher.group(1);
            String secondGroup = matcher.group(2);
            if (fileLength == 0) {
                if (firstGroup == null && secondGroup != null) {
                    for (int i = 0; i < secondGroup.length(); i++) {
                        if (secondGroup.charAt(i) != '0') {
                            satisfiableEmptyRange = true;
                            break;
                        }
                    }
                }
                continue;
            }
            long from = 0;
            long last = fileLength - 1;
            if (firstGroup != null) {
                from = Long.parseLong(firstGroup);
            }
            if (secondGroup != null) {
                long second = Long.parseLong(secondGroup);
                if (firstGroup == null) {
                    from = Math.max(0, fileLength - second);
                    last = fileLength - 1;
                } else {
                    last = Math.min(second, fileLength - 1);
                }
            }
            parts.add(ByteRangeRequest.create(from, last, fileLength));
        }
        if (!found) {
            throw new BadRequestException("Invalid range header");
        }
        if (fileLength == 0 && !satisfiableEmptyRange) {
            throw new HttpException("Wrong range", Status.REQUESTED_RANGE_NOT_SATISFIABLE_416, true)
                    .header(HeaderValues.create(HeaderNames.CONTENT_RANGE, "*/0"));
        }

        return parts;
    }

    private static boolean ifRangeMatches(ServerRequest req, String etag, boolean weakEtag) {
        if (!req.headers().contains(HeaderNames.IF_RANGE)) {
            return true;
        }

        String ifRange = req.headers().get(HeaderNames.IF_RANGE).get().trim();
        if (ifRange.startsWith("\"") || StaticContentHandler.isWeakETag(ifRange)) {
            return !weakEtag
                    && etag != null
                    && !StaticContentHandler.isWeakETag(ifRange)
                    && StaticContentHandler.unquoteETag(ifRange).equals(StaticContentHandler.unquoteETag(etag));
        }

        return false;
    }

    private static ByteRangeRequest create(long offset, long last, long fileLength) {
        if (offset >= fileLength || last < offset) {
            throw new HttpException("Wrong range", Status.REQUESTED_RANGE_NOT_SATISFIABLE_416, true)
                    .header(HeaderValues.create(HeaderNames.CONTENT_RANGE, "*/" + fileLength));
        }

        last = Math.min(last, fileLength - 1);
        long length = (last - offset) + 1;

        return new ByteRangeRequest(fileLength, offset, length);
    }
}
