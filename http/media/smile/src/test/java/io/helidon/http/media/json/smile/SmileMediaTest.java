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

package io.helidon.http.media.json.smile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaSupport;
import io.helidon.json.JsonGenerator;
import io.helidon.json.binding.Json;
import io.helidon.json.smile.SmileConfig;
import io.helidon.json.smile.SmileGenerator;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/*
When adding/updating tests in this class, consider if it should be done
 in other HTTP media support tests.
 */
class SmileMediaTest {
    private static final byte[] SMILE_HEADER_DEFAULT = new byte[] {0x3A, 0x29, 0x0A, 0x01};
    private static final GenericType<Book> BOOK_TYPE = GenericType.create(Book.class);
    private static final GenericType<List<Book>> BOOK_LIST_TYPE = new GenericType<List<Book>>() { };
    private final MediaSupport support;

    SmileMediaTest() {
        this.support = SmileSupport.create();
        support.init(MediaContext.create());
    }

    @Test
    void testWriteSingle() {
        WritableHeaders<?> headers = WritableHeaders.create();

        MediaSupport.WriterResponse<Book> res = support.writer(BOOK_TYPE, headers);
        assertThat(res.support(), is(MediaSupport.SupportLevel.COMPATIBLE));

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        res.supplier().get()
                .write(BOOK_TYPE, new Book("test-title"), os, headers);

        byte[] expected = concat(SMILE_HEADER_DEFAULT,
                                 new byte[] {(byte) 0xFA, (byte) 0x84, 't', 'i', 't', 'l', 'e', (byte) 0x49,
                                         't', 'e', 's', 't', '-', 't', 'i', 't', 'l', 'e', (byte) 0xFB});
        assertArrayEquals(expected, os.toByteArray());

        assertThat(headers, HttpHeaderMatcher.hasHeader(HeaderValues.CONTENT_TYPE_SMILE));

        // sanity check, parse back to book
        Book sanity = support.reader(BOOK_TYPE, headers)
                .supplier()
                .get()
                .read(BOOK_TYPE, new ByteArrayInputStream(os.toByteArray()), headers);

        assertThat(sanity.getTitle(), is("test-title"));
    }

    @Test
    void testWriteList() {
        WritableHeaders<?> headers = WritableHeaders.create();

        MediaSupport.WriterResponse<List<Book>> res = support.writer(BOOK_LIST_TYPE, headers);
        assertThat(res.support(), is(MediaSupport.SupportLevel.COMPATIBLE));

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        List<Book> books = List.of(new Book("first"), new Book("second"), new Book("third"));
        res.supplier().get()
                .write(BOOK_LIST_TYPE, books, os, headers);

        byte[] expected = concat(SMILE_HEADER_DEFAULT,
                                 new byte[] {(byte) 0xF8,
                                         (byte) 0xFA, (byte) 0x84, 't', 'i', 't', 'l', 'e',
                                         (byte) 0x44, 'f', 'i', 'r', 's', 't',
                                         (byte) 0xFB,
                                         (byte) 0xFA, 0x40, (byte) 0x45, 's', 'e', 'c', 'o', 'n', 'd', (byte) 0xFB,
                                         (byte) 0xFA, 0x40, (byte) 0x44, 't', 'h', 'i', 'r', 'd', (byte) 0xFB,
                                         (byte) 0xF9});
        assertArrayEquals(expected, os.toByteArray());

        assertThat(headers, HttpHeaderMatcher.hasHeader(HeaderValues.CONTENT_TYPE_SMILE));

        // sanity check, parse back to books
        List<Book> sanity = support.reader(BOOK_LIST_TYPE, headers)
                .supplier()
                .get()
                .read(BOOK_LIST_TYPE, new ByteArrayInputStream(os.toByteArray()), headers);

        assertThat(sanity, hasItems(new Book("first"), new Book("second"), new Book("third")));
    }

    @Test
    void testReadServerSingle() {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        requestHeaders.contentType(MediaTypes.APPLICATION_X_JACKSON_SMILE);

        MediaSupport.ReaderResponse<Book> res = support.reader(BOOK_TYPE, requestHeaders);
        assertThat(res.support(), is(MediaSupport.SupportLevel.COMPATIBLE));

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(os)) {
            generator.writeObjectStart()
                    .write("title", "utf-8: řžýčň")
                    .writeObjectEnd();
        }

        byte[] expected = concat(SMILE_HEADER_DEFAULT,
                                 new byte[] {(byte) 0xFA,
                                         (byte) 0x84, 't', 'i', 't', 'l', 'e',
                                         (byte) 0x8F, 'u', 't', 'f', '-', '8', ':', ' ',
                                         (byte) 0xC5, (byte) 0x99,
                                         (byte) 0xC5, (byte) 0xBE,
                                         (byte) 0xC3, (byte) 0xBD,
                                         (byte) 0xC4, (byte) 0x8D,
                                         (byte) 0xC5, (byte) 0x88,
                                         (byte) 0xFB});
        assertArrayEquals(expected, os.toByteArray());

        InputStream is = new ByteArrayInputStream(os.toByteArray());
        Book book = res.supplier().get()
                .read(BOOK_TYPE, is, requestHeaders);

        assertThat(book.getTitle(), is("utf-8: řžýčň"));
    }

    @Test
    void testReadClientSingle() {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        WritableHeaders<?> responseHeaders = WritableHeaders.create();
        requestHeaders.contentType(MediaTypes.APPLICATION_XML);
        responseHeaders.contentType(MediaTypes.APPLICATION_X_JACKSON_SMILE);

        MediaSupport.ReaderResponse<Book> res = support.reader(BOOK_TYPE, requestHeaders, responseHeaders);
        assertThat(res.support(), is(MediaSupport.SupportLevel.COMPATIBLE));

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(os)) {
            generator.writeObjectStart()
                    .write("title", "utf-8: řžýčň")
                    .writeObjectEnd();
        }

        byte[] expected = concat(SMILE_HEADER_DEFAULT,
                                 new byte[] {(byte) 0xFA, (byte) 0x84, 't', 'i', 't', 'l', 'e',
                                         (byte) 0x8F, 'u', 't', 'f', '-', '8', ':', ' ',
                                         (byte) 0xC5, (byte) 0x99,
                                         (byte) 0xC5, (byte) 0xBE,
                                         (byte) 0xC3, (byte) 0xBD,
                                         (byte) 0xC4, (byte) 0x8D,
                                         (byte) 0xC5, (byte) 0x88,
                                         (byte) 0xFB});
        assertArrayEquals(expected, os.toByteArray());

        InputStream is = new ByteArrayInputStream(os.toByteArray());
        Book book = res.supplier().get()
                .read(BOOK_TYPE, is, requestHeaders, responseHeaders);

        assertThat(book.getTitle(), is("utf-8: řžýčň"));
    }

    @Test
    void testReadServerList() {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        requestHeaders.contentType(MediaTypes.APPLICATION_X_JACKSON_SMILE);

        MediaSupport.ReaderResponse<List<Book>> res = support.reader(BOOK_LIST_TYPE, requestHeaders);
        assertThat(res.support(), is(MediaSupport.SupportLevel.COMPATIBLE));

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(os)) {
            generator.writeArrayStart()
                    .writeObjectStart()
                    .write("title", "first")
                    .writeObjectEnd()
                    .writeObjectStart()
                    .write("title", "second")
                    .writeObjectEnd()
                    .writeArrayEnd();
        }

        byte[] expected = concat(SMILE_HEADER_DEFAULT,
                                 new byte[] {(byte) 0xF8,
                                         (byte) 0xFA, (byte) 0x84, 't', 'i', 't', 'l', 'e',
                                         (byte) 0x44, 'f', 'i', 'r', 's', 't', (byte) 0xFB,
                                         (byte) 0xFA, 0x40, (byte) 0x45, 's', 'e', 'c', 'o', 'n', 'd', (byte) 0xFB,
                                         (byte) 0xF9});
        assertArrayEquals(expected, os.toByteArray());

        InputStream is = new ByteArrayInputStream(os.toByteArray());
        List<Book> books = res.supplier().get()
                .read(BOOK_LIST_TYPE, is, requestHeaders);

        assertThat(books, hasItems(new Book("first"), new Book("second")));
    }

    @Test
    void testWriteWithSharedValueStringsEnabled() {
        MediaSupport configuredSupport = SmileSupport.create(builder -> builder.smileConfig(SmileConfig.builder()
                                                                                                     .sharedValueStrings(true)
                                                                                                     .build()));
        configuredSupport.init(MediaContext.create());

        WritableHeaders<?> headers = WritableHeaders.create();
        MediaSupport.WriterResponse<List<Book>> res = configuredSupport.writer(BOOK_LIST_TYPE, headers);
        assertThat(res.support(), is(MediaSupport.SupportLevel.COMPATIBLE));

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        List<Book> books = List.of(new Book("repeat"), new Book("repeat"));

        res.supplier().get().write(BOOK_LIST_TYPE, books, os, headers);

        byte[] actual = os.toByteArray();
        byte[] expected = new byte[] {
                0x3A, 0x29, 0x0A, 0x03,
                (byte) 0xF8,
                (byte) 0xFA,
                (byte) 0x84, 't', 'i', 't', 'l', 'e',
                (byte) 0x45, 'r', 'e', 'p', 'e', 'a', 't',
                (byte) 0xFB,
                (byte) 0xFA,
                0x40,
                0x01,
                (byte) 0xFB,
                (byte) 0xF9
        };

        assertArrayEquals(expected, actual);

        List<Book> sanity = configuredSupport.reader(BOOK_LIST_TYPE, headers)
                .supplier()
                .get()
                .read(BOOK_LIST_TYPE, new ByteArrayInputStream(actual), headers);

        assertThat(sanity, hasItems(new Book("repeat"), new Book("repeat")));
    }

    @Test
    void testWriteWithSharedKeyStringsDisabled() {
        MediaSupport configuredSupport = SmileSupport.create(builder -> builder.smileConfig(SmileConfig.builder()
                                                                                                     .sharedKeyStrings(false)
                                                                                                     .build()));
        configuredSupport.init(MediaContext.create());

        WritableHeaders<?> headers = WritableHeaders.create();
        MediaSupport.WriterResponse<List<Book>> res = configuredSupport.writer(BOOK_LIST_TYPE, headers);
        assertThat(res.support(), is(MediaSupport.SupportLevel.COMPATIBLE));

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        List<Book> books = List.of(new Book("first"), new Book("second"));

        res.supplier().get().write(BOOK_LIST_TYPE, books, os, headers);

        byte[] actual = os.toByteArray();
        byte[] expected = new byte[] {
                0x3A, 0x29, 0x0A, 0x00,
                (byte) 0xF8,
                (byte) 0xFA,
                (byte) 0x84, 't', 'i', 't', 'l', 'e',
                (byte) 0x44, 'f', 'i', 'r', 's', 't',
                (byte) 0xFB,
                (byte) 0xFA,
                (byte) 0x84, 't', 'i', 't', 'l', 'e',
                (byte) 0x45, 's', 'e', 'c', 'o', 'n', 'd',
                (byte) 0xFB,
                (byte) 0xF9
        };
        assertArrayEquals(expected, actual);

        List<Book> sanity = configuredSupport.reader(BOOK_LIST_TYPE, headers)
                .supplier()
                .get()
                .read(BOOK_LIST_TYPE, new ByteArrayInputStream(actual), headers);

        assertThat(sanity, hasItems(new Book("first"), new Book("second")));
    }

    @Test
    void testWriteWithEmitEndMarkEnabled() {
        MediaSupport configuredSupport = SmileSupport.create(builder -> builder.smileConfig(SmileConfig.builder()
                                                                                                     .emitEndMark(true)
                                                                                                     .build()));
        configuredSupport.init(MediaContext.create());

        WritableHeaders<?> headers = WritableHeaders.create();
        MediaSupport.WriterResponse<Book> res = configuredSupport.writer(BOOK_TYPE, headers);
        assertThat(res.support(), is(MediaSupport.SupportLevel.COMPATIBLE));

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        res.supplier().get().write(BOOK_TYPE, new Book("done"), os, headers);

        byte[] actual = os.toByteArray();
        byte[] expected = new byte[] {
                0x3A, 0x29, 0x0A, 0x01,
                (byte) 0xFA,
                (byte) 0x84, 't', 'i', 't', 'l', 'e',
                (byte) 0x43, 'd', 'o', 'n', 'e',
                (byte) 0xFB,
                (byte) 0xFF
        };
        assertArrayEquals(expected, actual);
        assertThat(actual[actual.length - 1], is((byte) 0xFF));

        Book sanity = configuredSupport.reader(BOOK_TYPE, headers)
                .supplier()
                .get()
                .read(BOOK_TYPE, new ByteArrayInputStream(actual), headers);

        assertThat(sanity.getTitle(), is("done"));
    }

    @Test
    void testWriteWithRawBinaryEnabled() {
        MediaSupport configuredSupport = SmileSupport.create(builder -> builder.smileConfig(SmileConfig.builder()
                                                                                                     .rawBinaryEnabled(true)
                                                                                                     .build()));
        configuredSupport.init(MediaContext.create());

        WritableHeaders<?> headers = WritableHeaders.create();
        MediaSupport.WriterResponse<BinaryPayload> res = configuredSupport.writer(GenericType.create(BinaryPayload.class), headers);
        assertThat(res.support(), is(MediaSupport.SupportLevel.COMPATIBLE));

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        res.supplier().get().write(GenericType.create(BinaryPayload.class), new BinaryPayload("blob",
                                                                                              new byte[] {1, 2, 3, 4, 5}),
                                   os,
                                   headers);

        byte[] actual = os.toByteArray();
        byte[] expected = new byte[] {
                0x3A, 0x29, 0x0A, 0x05,
                (byte) 0xFA,
                (byte) 0x83, 'n', 'a', 'm', 'e',
                (byte) 0x43, 'b', 'l', 'o', 'b',
                (byte) 0x83, 'd', 'a', 't', 'a',
                (byte) 0xFD, (byte) 0x85, 0x01, 0x02, 0x03, 0x04, 0x05,
                (byte) 0xFB
        };
        assertArrayEquals(expected, actual);

        BinaryPayload sanity = configuredSupport.reader(GenericType.create(BinaryPayload.class), headers)
                .supplier()
                .get()
                .read(GenericType.create(BinaryPayload.class), new ByteArrayInputStream(actual), headers);

        assertThat(sanity.name(), is("blob"));
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, sanity.data());
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] out = new byte[first.length + second.length];
        System.arraycopy(first, 0, out, 0, first.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }

    @Json.Entity
    record BinaryPayload(String name, byte[] data) {
    }

    @Json.Entity
    public static class Book {
        private String title;

        public Book() {
        }

        public Book(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Book book = (Book) o;
            return Objects.equals(title, book.title);
        }

        @Override
        public int hashCode() {
            return Objects.hash(title);
        }

        @Override
        public String toString() {
            return title;
        }
    }

}
