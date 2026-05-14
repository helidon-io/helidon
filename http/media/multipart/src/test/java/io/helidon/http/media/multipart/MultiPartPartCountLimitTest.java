/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.http.media.multipart;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.http.media.MediaContext;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultiPartPartCountLimitTest {
    private static final String BOUNDARY = "part-count-boundary";
    private static final int MAX_ALLOWED_PARTS = 1000;

    @Test
    void acceptsMaximumAllowedParts() {
        assertThat(parseParts(MAX_ALLOWED_PARTS), is(MAX_ALLOWED_PARTS));
    }

    @Test
    void rejectsTooManyParts() {
        MultiPartImpl multiPart = multiPart(MAX_ALLOWED_PARTS + 1);

        for (int i = 0; i < MAX_ALLOWED_PARTS; i++) {
            assertThat(multiPart.hasNext(), is(true));
            ReadablePart part = multiPart.next();
            assertThat(part.name(), is("part-" + i));
            part.consume();
        }

        HttpException exception = assertThrows(HttpException.class,
                                               multiPart::hasNext);

        assertThat(exception.getMessage(), containsString("Maximum multipart part count exceeded"));
        assertThat(exception.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
    }

    private static int parseParts(int partCount) {
        MultiPartImpl multiPart = multiPart(partCount);

        int parsedParts = 0;
        while (multiPart.hasNext()) {
            multiPart.next().consume();
            parsedParts++;
        }
        return parsedParts;
    }

    private static MultiPartImpl multiPart(int partCount) {
        StringBuilder payload = new StringBuilder(partCount * 96);
        for (int i = 0; i < partCount; i++) {
            payload.append("--")
                    .append(BOUNDARY)
                    .append("\r\n")
                    .append("Content-Disposition: form-data; name=\"part-")
                    .append(i)
                    .append("\"\r\n")
                    .append("Content-Length: 0\r\n\r\n\r\n");
        }
        payload.append("--")
                .append(BOUNDARY)
                .append("--\r\n");

        return new MultiPartImpl(MediaContext.create(),
                                 BOUNDARY,
                                 new ByteArrayInputStream(payload.toString()
                                                                  .getBytes(StandardCharsets.US_ASCII)));
    }
}
