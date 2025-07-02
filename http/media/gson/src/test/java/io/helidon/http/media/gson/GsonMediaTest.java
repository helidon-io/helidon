/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.http.media.gson;

import io.helidon.common.GenericType;
import io.helidon.common.config.Config;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpMediaType;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaSupport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

/*
When adding/updating tests in this class, consider if it should be done
 in the following tests a well:
    - JacksonMediaTest
    - JsonbMediaTest
    - JsonpMediaTest
 */
class GsonMediaTest {
    private static final Charset ISO_8859_2 = Charset.forName("ISO-8859-2");
    private static final GenericType<Book> BOOK_TYPE = GenericType.create(Book.class);
    private static final GenericType<List<Book>> BOOK_LIST_TYPE = new GenericType<List<Book>>() { };
    private final MediaSupport support;

    GsonMediaTest() {
        this.support = GsonSupport.create(Config.empty());
        support.init(MediaContext.create());
    }

    @Test
    void testWriteSingle() {
        WritableHeaders<?> headers = WritableHeaders.create();

        MediaSupport.WriterResponse<Book> res = support.writer(BOOK_TYPE, headers);
        assertThat(res.support(), is(MediaSupport.SupportLevel.SUPPORTED));

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        res.supplier().get()
                .write(BOOK_TYPE, new Book("test-title"), os, headers);

        assertThat(headers, HttpHeaderMatcher.hasHeader(HeaderValues.CONTENT_TYPE_JSON));
        String result = os.toString(StandardCharsets.UTF_8);
        assertThat(result, containsString("\"title\""));
        assertThat(result, containsString("\"test-title\""));

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
        assertThat(res.support(), is(MediaSupport.SupportLevel.SUPPORTED));

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        List<Book> books = List.of(new Book("first"), new Book("second"), new Book("third"));
        res.supplier().get()
                .write(BOOK_LIST_TYPE, books, os, headers);

        assertThat(headers, HttpHeaderMatcher.hasHeader(HeaderValues.CONTENT_TYPE_JSON));
        String result = os.toString(StandardCharsets.UTF_8);
        assertThat(result, containsString("\"title\""));
        assertThat(result, containsString("\"first\""));
        assertThat(result, containsString("\"second\""));
        assertThat(result, containsString("\"third\""));

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
        requestHeaders.contentType(MediaTypes.APPLICATION_JSON);

        MediaSupport.ReaderResponse<Book> res = support.reader(BOOK_TYPE, requestHeaders);
        assertThat(res.support(), is(MediaSupport.SupportLevel.COMPATIBLE));

        InputStream is = new ByteArrayInputStream("{\"title\": \"utf-8: řžýčň\"}".getBytes(StandardCharsets.UTF_8));
        Book book = res.supplier().get()
                .read(BOOK_TYPE, is, requestHeaders);

        assertThat(book.getTitle(), is("utf-8: řžýčň"));
    }

    @Test
    void testReadClientSingle() {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        WritableHeaders<?> responseHeaders = WritableHeaders.create();
        requestHeaders.contentType(MediaTypes.APPLICATION_XML);
        responseHeaders.contentType(MediaTypes.APPLICATION_JSON);

        MediaSupport.ReaderResponse<Book> res = support.reader(BOOK_TYPE, requestHeaders, responseHeaders);
        assertThat(res.support(), is(MediaSupport.SupportLevel.COMPATIBLE));

        InputStream is = new ByteArrayInputStream("{\"title\": \"utf-8: řžýčň\"}".getBytes(StandardCharsets.UTF_8));
        Book book = res.supplier().get()
                .read(BOOK_TYPE, is, requestHeaders, responseHeaders);

        assertThat(book.getTitle(), is("utf-8: řžýčň"));
    }

    @Test
    void testReadServerList() {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        requestHeaders.contentType(MediaTypes.APPLICATION_JSON);

        MediaSupport.ReaderResponse<List<Book>> res = support.reader(BOOK_LIST_TYPE, requestHeaders);
        assertThat(res.support(), is(MediaSupport.SupportLevel.COMPATIBLE));

        InputStream is =
                new ByteArrayInputStream("[{\"title\": \"first\"}, {\"title\": \"second\"}]".getBytes(StandardCharsets.UTF_8));
        List<Book> books = res.supplier().get()
                .read(BOOK_LIST_TYPE, is, requestHeaders);

        assertThat(books, hasItems(new Book("first"), new Book("second")));
    }

    @Test
    void testReadServerSingleNonUtf8() {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        requestHeaders.contentType(HttpMediaType.create(MediaTypes.APPLICATION_JSON).withCharset(ISO_8859_2));

        MediaSupport.ReaderResponse<Book> res = support.reader(BOOK_TYPE, requestHeaders);
        assertThat(res.support(), is(MediaSupport.SupportLevel.COMPATIBLE));

        InputStream is = new ByteArrayInputStream("{\"title\": \"is-8859-2: řžýčň\"}".getBytes(ISO_8859_2));
        Book book = res.supplier().get()
                .read(BOOK_TYPE, is, requestHeaders);

        assertThat(book.getTitle(), is("is-8859-2: řžýčň"));
    }

    @Test
    void testReadClientSingleNonUtf8() {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        WritableHeaders<?> responseHeaders = WritableHeaders.create();
        requestHeaders.contentType(MediaTypes.APPLICATION_XML);
        responseHeaders.contentType(HttpMediaType.create(MediaTypes.APPLICATION_JSON).withCharset(ISO_8859_2));

        MediaSupport.ReaderResponse<Book> res = support.reader(BOOK_TYPE, requestHeaders, responseHeaders);
        assertThat(res.support(), is(MediaSupport.SupportLevel.COMPATIBLE));

        InputStream is = new ByteArrayInputStream("{\"title\": \"utf-8: řžýčň\"}".getBytes(ISO_8859_2));
        Book book = res.supplier().get()
                .read(BOOK_TYPE, is, requestHeaders, responseHeaders);

        assertThat(book.getTitle(), is("utf-8: řžýčň"));
    }

    @Test
    void testReadServerListNonUtf8() {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        requestHeaders.contentType(HttpMediaType.create(MediaTypes.APPLICATION_JSON).withCharset(ISO_8859_2));

        MediaSupport.ReaderResponse<List<Book>> res = support.reader(BOOK_LIST_TYPE, requestHeaders);
        assertThat(res.support(), is(MediaSupport.SupportLevel.COMPATIBLE));

        InputStream is =
                new ByteArrayInputStream("[{\"title\": \"čř\"}, {\"title\": \"šň\"}]".getBytes(ISO_8859_2));
        List<Book> books = res.supplier().get()
                .read(BOOK_LIST_TYPE, is, requestHeaders);

        assertThat(books, hasItems(new Book("čř"), new Book("šň")));
    }

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
