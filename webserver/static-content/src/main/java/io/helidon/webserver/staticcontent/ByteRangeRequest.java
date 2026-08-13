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

import io.helidon.http.BadRequestException;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerRequest;

record ByteRangeRequest(long fileLength, long offset, long length) {
    private static final String BYTES_UNIT = "bytes=";

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
        if (!startsWithBytesUnit(headerValues)) {
            return List.of();
        }

        List<ByteRangeRequest> parts = new ArrayList<>();
        boolean sawRange = false;
        boolean satisfiableEmptyRange = false;
        int start = 0;
        while (start < headerValues.length()) {
            int comma = headerValues.indexOf(',', start);
            String part = headerValues.substring(start, comma == -1 ? headerValues.length() : comma).trim();
            if (startsWithBytesUnit(part)) {
                part = part.substring(BYTES_UNIT.length()).trim();
            }
            if (part.isEmpty()) {
                if (comma == -1) {
                    if (sawRange) {
                        break;
                    }
                    throw new BadRequestException("Invalid range header");
                }
                start = comma + 1;
                continue;
            }
            sawRange = true;

            int dash = part.indexOf('-');
            if (dash == -1 || dash != part.lastIndexOf('-')) {
                throw new BadRequestException("Invalid range header");
            }

            String firstGroup = part.substring(0, dash);
            String secondGroup = part.substring(dash + 1);
            if (firstGroup.isEmpty() && secondGroup.isEmpty()) {
                throw new BadRequestException("Invalid range header");
            }

            //"bytes=0-1023" - 0 to 1023 (included both)
            // 500- (= 500 until end)
            // -500 (= last 500)
            // 0-0,-1 (first and last)
            long from = 0;
            long last = fileLength - 1;
            if (!firstGroup.isEmpty()) {
                from = parseLong(firstGroup);
            }
            if (!secondGroup.isEmpty()) {
                long second = parseLong(secondGroup);
                if (firstGroup.isEmpty()) {
                    if (fileLength == 0 && second != 0) {
                        satisfiableEmptyRange = true;
                    }
                    if (second == 0 || fileLength == 0) {
                        if (comma == -1) {
                            break;
                        }
                        start = comma + 1;
                        continue;
                    }
                    from = Math.max(fileLength - second, 0);
                } else {
                    last = second;
                }
            }

            if (!firstGroup.isEmpty() && !secondGroup.isEmpty()) {
                // Long.MAX_VALUE is the supported parsing boundary; ignore ranges at or beyond it.
                if (from == Long.MAX_VALUE && last == Long.MAX_VALUE) {
                    return List.of();
                }
                if (last < from) {
                    throw new BadRequestException("Invalid range header");
                }
            }

            last = Math.min(last, fileLength - 1);
            if (from < fileLength && last >= from) {
                long length = (last - from) + 1;
                parts.add(new ByteRangeRequest(fileLength, from, length));
            }

            if (comma == -1) {
                break;
            }
            start = comma + 1;
        }

        if (fileLength == 0 && satisfiableEmptyRange) {
            return List.of();
        }
        if (parts.isEmpty()) {
            throw new HttpException("Wrong range", Status.REQUESTED_RANGE_NOT_SATISFIABLE_416, true)
                    .header(HeaderValues.create(HeaderNames.CONTENT_RANGE, "bytes */" + fileLength));
        }

        return parts;
    }

    private static boolean ifRangeMatches(ServerRequest req, String etag, boolean weakEtag) {
        if (!req.headers().contains(HeaderNames.IF_RANGE)) {
            return true;
        }

        Header ifRangeHeader = req.headers().get(HeaderNames.IF_RANGE);
        if (ifRangeHeader.valueCount() != 1) {
            return false;
        }

        String ifRange = ifRangeHeader.get().trim();
        if (ifRange.startsWith("\"") || StaticContentHandler.isWeakETag(ifRange)) {
            return !weakEtag
                    && etag != null
                    && !StaticContentHandler.isWeakETag(ifRange)
                    && StaticContentHandler.unquoteETag(ifRange).equals(StaticContentHandler.unquoteETag(etag));
        }

        return false;
    }

    private static long parseLong(String value) {
        long result = 0;
        boolean overflow = false;
        for (int i = 0; i < value.length(); i++) {
            int digit = value.charAt(i) - '0';
            if (digit < 0 || digit > 9) {
                throw new BadRequestException("Invalid range header");
            }
            if (!overflow) {
                if (result > (Long.MAX_VALUE - digit) / 10) {
                    overflow = true;
                } else {
                    result = (result * 10) + digit;
                }
            }
        }
        return overflow ? Long.MAX_VALUE : result;
    }

    private static boolean startsWithBytesUnit(String value) {
        return value.regionMatches(true, 0, BYTES_UNIT, 0, BYTES_UNIT.length());
    }
}
