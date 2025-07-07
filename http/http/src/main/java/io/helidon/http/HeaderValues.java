/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;

import io.helidon.common.buffers.LazyString;

/**
 * Values of commonly used headers.
 */
public final class HeaderValues {
    /**
     * Accept byte ranges for file download.
     */
    public static final Header ACCEPT_RANGES_BYTES = createCached(HeaderNames.ACCEPT_RANGES, "bytes");
    /**
     * Not accepting byte ranges for file download.
     */
    public static final Header ACCEPT_RANGES_NONE = createCached(HeaderNames.ACCEPT_RANGES, "none");
    /**
     * Chunked transfer encoding.
     * Used in {@code HTTP/1}.
     */
    public static final Header TRANSFER_ENCODING_CHUNKED = createCached(HeaderNames.TRANSFER_ENCODING, "chunked");
    /**
     * Connection keep-alive.
     * Used in {@code HTTP/1}.
     */
    public static final Header CONNECTION_KEEP_ALIVE = createCached(HeaderNames.CONNECTION, "keep-alive");
    /**
     * Connection close.
     * Used in {@code HTTP/1}.
     */
    public static final Header CONNECTION_CLOSE = createCached(HeaderNames.CONNECTION, "close");
    /**
     * Content type application/json with no charset.
     */
    public static final Header CONTENT_TYPE_JSON = createCached(HeaderNames.CONTENT_TYPE, "application/json");
    /**
     * Content type text plain with no charset.
     */
    public static final Header CONTENT_TYPE_TEXT_PLAIN = createCached(HeaderNames.CONTENT_TYPE, "text/plain");
    /**
     * Content type octet stream.
     */
    public static final Header CONTENT_TYPE_OCTET_STREAM = createCached(HeaderNames.CONTENT_TYPE,
                                                                        "application/octet-stream");
    /**
     * Content type SSE event stream.
     */
    public static final Header CONTENT_TYPE_EVENT_STREAM = createCached(HeaderNames.CONTENT_TYPE,
                                                                        "text/event-stream");

    /**
     * Accept application/json.
     */
    public static final Header ACCEPT_JSON = createCached(HeaderNames.ACCEPT, "application/json");
    /**
     * Accept text/plain with UTF-8.
     */
    public static final Header ACCEPT_TEXT = createCached(HeaderNames.ACCEPT, "text/plain;charset=UTF-8");
    /**
     * Accept text/event-stream.
     */
    public static final Header ACCEPT_EVENT_STREAM = createCached(HeaderNames.ACCEPT, "text/event-stream");
    /**
     * Expect 100 header.
     */
    public static final Header EXPECT_100 = createCached(HeaderNames.EXPECT, "100-continue");
    /**
     * Content length with 0 value.
     */
    public static final Header CONTENT_LENGTH_ZERO = createCached(HeaderNames.CONTENT_LENGTH, "0");
    /**
     * Cache control without any caching.
     */
    public static final Header CACHE_NO_CACHE = create(HeaderNames.CACHE_CONTROL, "no-cache",
                                                       "no-store",
                                                       "must-revalidate",
                                                       "no-transform");
    /**
     * Cache control that allows caching with no transform.
     */
    public static final Header CACHE_NORMAL = createCached(HeaderNames.CACHE_CONTROL, "no-transform");

    /**
     * Discourage browsers from attempting to detect the content type by "sniffing" the data.
     */
    public static final Header X_CONTENT_TYPE_OPTIONS_NOSNIFF = createCached(HeaderNames.X_CONTENT_TYPE_OPTIONS, "nosniff");

    /**
     * TE header set to {@code trailers}, used to enable trailer headers.
     */
    public static final Header TE_TRAILERS = createCached(HeaderNames.TE, "trailers");

    private HeaderValues() {
    }

    /**
     * Create and cache byte value.
     * Use this method if the header value is stored in a constant, or used repeatedly.
     *
     * @param name  header name
     * @param value value of the header
     * @return a new header
     */
    public static Header createCached(String name, String value) {
        return createCached(HeaderNames.create(name), value);
    }

    /**
     * Create and cache byte value.
     * Use this method if the header value is stored in a constant, or used repeatedly.
     *
     * @param name  header name
     * @param value value of the header
     * @return a new header
     */
    public static Header createCached(String name, int value) {
        return createCached(HeaderNames.create(name), value);
    }

    /**
     * Create and cache byte value.
     * Use this method if the header value is stored in a constant, or used repeatedly.
     *
     * @param name  header name
     * @param value value of the header
     * @return a new header
     */
    public static Header createCached(String name, long value) {
        return createCached(HeaderNames.create(name), value);
    }

    /**
     * Create and cache byte value.
     * Use this method if the header value is stored in a constant, or used repeatedly.
     *
     * @param name  header name
     * @param value value of the header
     * @return a new header
     */
    public static Header createCached(HeaderName name, String value) {
        return new HeaderValueCached(name, false,
                                     false,
                                     value.getBytes(StandardCharsets.US_ASCII),
                                     value);
    }

    /**
     * Create and cache byte value.
     * Use this method if the header value is stored in a constant, or used repeatedly.
     *
     * @param name  header name
     * @param value value of the header
     * @return a new header
     */
    public static Header createCached(HeaderName name, int value) {
        return createCached(name, String.valueOf(value));
    }

    /**
     * Create and cache byte value.
     * Use this method if the header value is stored in a constant, or used repeatedly.
     *
     * @param name  header name
     * @param value value of the header
     * @return a new header
     */
    public static Header createCached(HeaderName name, long value) {
        return createCached(name, String.valueOf(value));
    }

    /**
     * Create a new header with a single value. This header is considered unchanging and not sensitive.
     *
     * @param name  name of the header
     * @param value lazy string with the value
     * @return a new header
     * @see #create(io.helidon.http.HeaderName, boolean, boolean, String...)
     */
    public static Header create(HeaderName name, LazyString value) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);

        return new HeaderValueLazy(name, false, false, value);
    }

    /**
     * Create a new header with a single value. This header is considered unchanging and not sensitive.
     *
     * @param name  name of the header
     * @param value integer value of the header
     * @return a new header
     * @see #create(io.helidon.http.HeaderName, boolean, boolean, String...)
     */
    public static Header create(HeaderName name, int value) {
        Objects.requireNonNull(name);

        return new HeaderValueSingle(name, false, false, String.valueOf(value));
    }

    /**
     * Create a new header with a single value. This header is considered unchanging and not sensitive.
     *
     * @param name  name of the header
     * @param value long value of the header
     * @return a new header
     * @see #create(io.helidon.http.HeaderName, boolean, boolean, String...)
     */
    public static Header create(HeaderName name, long value) {
        Objects.requireNonNull(name);

        return new HeaderValueSingle(name, false, false, String.valueOf(value));
    }

    /**
     * Create a new header with a single value. This header is considered unchanging and not sensitive.
     *
     * @param name  name of the header
     * @param value value of the header
     * @return a new header
     * @see #create(io.helidon.http.HeaderName, boolean, boolean, String...)
     */
    public static Header create(HeaderName name, String value) {
        Objects.requireNonNull(name, "HeaderName must not be null");
        Objects.requireNonNull(value, "HeaderValue must not be null");

        return new HeaderValueSingle(name,
                                     false,
                                     false,
                                     value);
    }

    /**
     * Create a new header with a single value. This header is considered unchanging and not sensitive.
     *
     * @param name  name of the header
     * @param value value of the header
     * @return a new header
     * @see #create(io.helidon.http.HeaderName, boolean, boolean, String...)
     */
    public static Header create(String name, String value) {
        Objects.requireNonNull(name, "Header name must not be null");

        return create(HeaderNames.create(name), value);
    }

    /**
     * Create a new header with a single value. This header is considered unchanging and not sensitive.
     *
     * @param name  name of the header
     * @param value value of the header
     * @return a new header
     * @see #create(io.helidon.http.HeaderName, boolean, boolean, String...)
     */
    public static Header create(String name, int value) {
        Objects.requireNonNull(name, "Header name must not be null");

        return create(HeaderNames.create(name), value);
    }

    /**
     * Create a new header with a single value. This header is considered unchanging and not sensitive.
     *
     * @param name  name of the header
     * @param value value of the header
     * @return a new header
     * @see #create(io.helidon.http.HeaderName, boolean, boolean, String...)
     */
    public static Header create(String name, long value) {
        Objects.requireNonNull(name, "Header name must not be null");

        return create(HeaderNames.create(name), value);
    }

    /**
     * Create a new header. This header is considered unchanging and not sensitive.
     *
     * @param name   name of the header
     * @param values values of the header, must contain at least one value (which may be an empty String)
     * @return a new header
     * @see #create(io.helidon.http.HeaderName, boolean, boolean, String...)
     * @throws java.lang.IllegalArgumentException in case the collection is empty
     */
    public static Header create(HeaderName name, String... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Cannot create a header without a value. Header: " + name);
        }
        return new HeaderValueArray(name, false, false, values);
    }

    /**
     * Create a new header. This header is considered unchanging and not sensitive.
     *
     * @param name   name of the header
     * @param values values of the header, must contain at least one value (which may be an empty String)
     * @return a new header
     * @see #create(io.helidon.http.HeaderName, boolean, boolean, String...)
     * @throws java.lang.IllegalArgumentException in case the collection is empty
     */
    public static Header create(String name, String... values) {
        return create(HeaderNames.create(name), values);
    }

    /**
     * Create a new header. This header is considered unchanging and not sensitive.
     *
     * @param name   name of the header
     * @param values values of the header, must contain at least one value (which may be an empty String)
     * @return a new header
     * @see #create(io.helidon.http.HeaderName, boolean, boolean, String...)
     * @throws java.lang.IllegalArgumentException in case the collection is empty
     */
    public static Header create(HeaderName name, Collection<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Cannot create a header without a value. Header: " + name);
        }
        return new HeaderValueList(name, false, false, values);
    }

    /**
     * Create a new header. This header is considered unchanging and not sensitive.
     *
     * @param name   name of the header
     * @param values values of the header, must contain at least one value (which may be an empty String)
     * @return a new header
     * @see #create(io.helidon.http.HeaderName, boolean, boolean, String...)
     * @throws java.lang.IllegalArgumentException in case the collection is empty
     */
    public static Header create(String name, Collection<String> values) {
        return create(HeaderNames.create(name), values);
    }

    /**
     * Create a new header. This header is considered unchanging and not sensitive.
     *
     * @param name   name of the header
     * @param values values of the header, must contain at least one value (which may be an empty String)
     * @return a new header
     * @see #create(io.helidon.http.HeaderName, boolean, boolean, String...)
     * @throws java.lang.IllegalArgumentException in case the collection is empty
     */
    public static Header create(HeaderName name, Iterable<String> values) {
        System.out.println("Header.create(HeaderName name, Iterable<String> values)");
        if (!values.iterator().hasNext()) {
            throw new IllegalArgumentException("Cannot create a header without a value. Header: " + name);
        }
        return new HeaderValueIterable(name, false, false, values);
    }

    /**
     * Create a new header. This header is considered unchanging and not sensitive.
     *
     * @param name   name of the header
     * @param values values of the header, must contain at least one value (which may be an empty String)
     * @return a new header
     * @see #create(io.helidon.http.HeaderName, boolean, boolean, String...)
     * @throws java.lang.IllegalArgumentException in case the collection is empty
     */
    public static Header create(String name, Iterable<String> values) {
        return create(HeaderNames.create(name), values);
    }

    /**
     * Create and cache byte value.
     * Use this method if the header value is stored in a constant, or used repeatedly.
     *
     * @param name      header name
     * @param changing  whether the value is changing often (to disable caching for HTTP/2)
     * @param sensitive whether the value is sensitive (to disable caching for HTTP/2)
     * @param value     value of the header
     * @return a new header
     */
    public static Header createCached(HeaderName name, boolean changing, boolean sensitive, String value) {
        return new HeaderValueCached(name, changing, sensitive, value.getBytes(StandardCharsets.UTF_8), value);
    }

    /**
     * Create a new header.
     *
     * @param name      name of the header
     * @param changing  whether the value is changing often (to disable caching for HTTP/2)
     * @param sensitive whether the value is sensitive (to disable caching for HTTP/2)
     * @param values    value(s) of the header
     * @return a new header
     */
    public static Header create(HeaderName name, boolean changing, boolean sensitive, String... values) {
        return new HeaderValueArray(name, changing, sensitive, values);
    }

    /**
     * Create a new header.
     *
     * @param name      name of the header
     * @param changing  whether the value is changing often (to disable caching for HTTP/2)
     * @param sensitive whether the value is sensitive (to disable caching for HTTP/2)
     * @param value     value of the header
     * @return a new header
     */
    public static Header create(HeaderName name, boolean changing, boolean sensitive, int value) {
        return create(name, changing, sensitive, String.valueOf(value));
    }

    /**
     * Create a new header.
     *
     * @param name      name of the header
     * @param changing  whether the value is changing often (to disable caching for HTTP/2)
     * @param sensitive whether the value is sensitive (to disable caching for HTTP/2)
     * @param value     value of the header
     * @return a new header
     */
    public static Header create(HeaderName name, boolean changing, boolean sensitive, long value) {
        return create(name, changing, sensitive, String.valueOf(value));
    }
}
