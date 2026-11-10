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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import io.helidon.http.HeaderNames;
import io.helidon.http.media.MediaContext;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultiPartFileNameTest {
    @Test
    void rawUtf8FileNames() {
        for (String filename : List.of("prüfenfile1.json", "日本語.json", "📄.json")) {
            ReadablePart part = part("upload", filename, StandardCharsets.UTF_8);

            assertThat(filename, part.fileName(), is(Optional.of(filename)));
            assertThat(part.name(), is("upload"));
            assertThat(part.as(String.class), is("{}"));
        }
    }

    @Test
    void rawUtf8FieldNameAndHeaderBytes() {
        ReadablePart part = part("příloha", "prüfenfile1.json", StandardCharsets.UTF_8);

        assertThat(part.name(), is("příloha"));
        assertThat(part.fileName(), is(Optional.of("prüfenfile1.json")));
        assertThat(new String(part.partHeaders().get(HeaderNames.CONTENT_DISPOSITION).valueBytes(), StandardCharsets.UTF_8),
                   is("form-data; name=\"příloha\"; filename=\"prüfenfile1.json\""));
    }

    @Test
    void percentEncodedFileName() {
        ReadablePart part = part("upload", "pr%C3%BCfenfile1.json", StandardCharsets.US_ASCII);

        assertThat(part.fileName(), is(Optional.of("prüfenfile1.json")));
    }

    @Test
    void legacyLatin1FileName() {
        ReadablePart part = part("upload", "prüfenfile1.json", StandardCharsets.ISO_8859_1);

        assertThat(part.fileName(), is(Optional.of("prüfenfile1.json")));
    }

    @Test
    void utf8FileNameUsesFinalPathSegment() {
        ReadablePart part = part("upload", "../příloha/prüfenfile1.json", StandardCharsets.UTF_8);

        assertThat(part.fileName(), is(Optional.of("prüfenfile1.json")));
    }

    @Test
    void controlCharactersRemainRejected() {
        for (String filename : List.of("prüfen%0afile.json", "prüfen\u0085file.json")) {
            ReadablePart part = part("upload", filename, StandardCharsets.UTF_8);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, part::fileName);
            assertThat(exception.getMessage(), containsString("control character"));
        }
    }

    private static ReadablePart part(String name, String filename, Charset charset) {
        String payload = "--test-boundary\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/json\r\n\r\n"
                + "{}\r\n--test-boundary--\r\n";
        var stream = new ByteArrayInputStream(payload.getBytes(charset)) {
            @Override
            public int read(byte[] bytes, int offset, int length) {
                // Exercise UTF-8 sequences split across reads.
                return super.read(bytes, offset, Math.min(length, 1));
            }
        };
        return new MultiPartImpl(MediaContext.create(), "test-boundary", stream).next();
    }
}
