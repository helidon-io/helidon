/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.http.Http.Header;
import io.helidon.http.Http.HeaderName;
import io.helidon.http.Http.HeaderValueWriteable;

@SuppressWarnings("unchecked")
class HeadersImpl<T extends WritableHeaders<T>> implements WritableHeaders<T> {
    static final int KNOWN_HEADER_SIZE = HeaderNameEnum.values().length;
    /*
     Optimization for most commonly used header names
     */
    private final Header[] knownHeaders = new Http.Header[KNOWN_HEADER_SIZE];
    // custom (unknown) headers are slower
    private final Map<HeaderName, Http.Header> customHeaders = new HashMap<>();
    private IntSet knownHeaderIndices = new IntSet(KNOWN_HEADER_SIZE);

    HeadersImpl() {
    }

    HeadersImpl(Headers headers) {
        for (Header header : headers) {
            set(header);
        }
    }

    @Override
    public List<String> all(HeaderName name, Supplier<List<String>> defaultSupplier) {
        Header headerValue = find(name);
        if (headerValue == null) {
            return defaultSupplier.get();
        }
        return headerValue.allValues();
    }

    @Override
    public boolean contains(HeaderName name) {
        return find(name) != null;
    }

    @Override
    public boolean contains(Http.Header headerWithValue) {
        Http.Header headerValue = find(headerWithValue.headerName());
        if (headerValue == null) {
            return false;
        }
        if (headerWithValue.valueCount() == 1 && headerValue.valueCount() == 1) {
            // just a string compare instead of list compare
            return headerWithValue.value().equals(headerValue.value());
        }
        return headerWithValue.allValues().equals(headerValue.allValues());
    }

    @Override
    public Http.Header get(HeaderName name) {
        Header headerValue = find(name);
        if (headerValue == null) {
            throw new NoSuchElementException("Header " + name + " is not present in these headers");
        }
        return headerValue;
    }

    @Override
    public int size() {
        return customHeaders.size() + knownHeaderIndices.size();
    }

    @Override
    public List<HttpMediaType> acceptedTypes() {
        if (contains(Http.HeaderNames.ACCEPT)) {
            List<String> accepts = get(Http.HeaderNames.ACCEPT).allValues(true);

            List<HttpMediaType> mediaTypes = new ArrayList<>(accepts.size());
            for (String accept : accepts) {
                mediaTypes.add(HttpMediaType.create(accept));
            }
            Collections.sort(mediaTypes);
            return mediaTypes;
        } else {
            return List.of();
        }
    }

    @Override
    public Iterator<Http.Header> iterator() {
        return new HeaderIterator();
    }

    @Override
    public T setIfAbsent(Header header) {
        Http.Header found = find(header.headerName());
        if (found == null) {
            set(header);
        }

        return (T) this;
    }

    @Override
    public T add(Header header) {
        HeaderName name = header.headerName();
        Http.Header headerValue = find(name);
        if (headerValue == null) {
            set(header);
        } else {
            HeaderValueWriteable writable;

            if (headerValue instanceof HeaderValueWriteable hvw) {
                writable = hvw;
            } else {
                writable = HeaderValueWriteable.create(header);
            }
            for (String value : header.allValues()) {
                writable.addValue(value);
            }
            set(writable);
        }
        return (T) this;
    }

    @Override
    public T remove(HeaderName name) {
        doRemove(name);
        return (T) this;
    }

    @Override
    public T remove(HeaderName name, Consumer<Http.Header> removedConsumer) {
        Http.Header remove = doRemove(name);
        if (remove != null) {
            removedConsumer.accept(remove);
        }
        return (T) this;
    }

    @Override
    public T set(Http.Header header) {
        HeaderName name = header.headerName();

        Header usedHeader = header;
        if (header instanceof HeaderValueWriteable) {
            // we must create a new instance, as we risk modifying state of the provided header
            usedHeader = new HeaderValueCopy(header);
        }
        int index = name.index();
        if (index == -1) {
            customHeaders.put(name, usedHeader);
        } else {
            knownHeaders[index] = usedHeader;
            knownHeaderIndices.add(index);
        }
        return (T) this;
    }

    @Override
    public T clear() {
        Arrays.fill(knownHeaders, null);
        knownHeaderIndices = new IntSet(KNOWN_HEADER_SIZE);
        customHeaders.clear();
        return (T) this;
    }

    @Override
    public T from(Headers headers) {
        for (Header header : headers) {
            set(header);
        }
        return (T) this;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        for (Http.Header headerValue : this) {
            for (String value : headerValue.allValues()) {
                builder.append(headerValue.name())
                        .append(": ");
                if (headerValue.sensitive()) {
                    builder.append("*".repeat(value.length()));
                } else {
                    builder.append(value);
                }
                List<String> details = new ArrayList<>(2);
                if (headerValue.sensitive()) {
                    details.add("sensitive");
                }
                if (headerValue.changing()) {
                    details.add("changing");
                }
                if (!details.isEmpty()) {
                    builder.append(" (");
                    builder.append(String.join(", ", details));
                    builder.append(")");
                }
                builder.append("\n");
            }
        }

        return builder.toString();
    }

    public Header doRemove(HeaderName name) {
        if (name instanceof HeaderNameEnum) {
            int index = ((HeaderNameEnum) name).ordinal();
            Http.Header value = knownHeaders[index];
            knownHeaders[index] = null;
            knownHeaderIndices.remove(index);
            return value;
        }
        return customHeaders.remove(name);
    }
    private Header find(HeaderName name) {
        int index = name.index();

        if (index > -1) {
            return knownHeaders[index];
        }

        return customHeaders.get(name);
    }

    private class HeaderIterator implements Iterator<Header> {
        private final boolean noCustom = customHeaders.isEmpty();

        private boolean inKnown = true;
        private int last = -1;
        private Iterator<Header> customHeadersIterator;

        @Override
        public boolean hasNext() {
            if (inKnown) {
                last = knownHeaderIndices.nextSetBit(last + 1);
                if (last >= 0) {
                    return true;
                }
                inKnown = false;
                if (noCustom) {
                    return false;
                }
                ensureCustom();
            }

            return customHeadersIterator.hasNext();
        }

        @Override
        public Header next() {
            if (last >= 0) {
                return knownHeaders[last];
            }

            ensureCustom();
            return customHeadersIterator.next();
        }

        private void ensureCustom() {
            if (customHeadersIterator == null) {
                customHeadersIterator = customHeaders.values().iterator();
            }
        }
    }
}
