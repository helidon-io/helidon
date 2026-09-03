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

package io.helidon.webclient.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.Api;
import io.helidon.common.GenericType;
import io.helidon.common.mapper.Value;
import io.helidon.common.uri.UriAuthority;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Status;

/**
 * Protocol-neutral context for a response received by a WebClient transport.
 */
@Api.Internal
public final class WebClientProtocolResponse {
    private final ResolvedClientTarget target;
    private final boolean explicitConnection;
    private final String protocolId;
    private final Status status;
    private final ClientResponseHeaders headers;
    private final Instant receivedAt;
    private final UriAuthority alternativeAuthority;

    private WebClientProtocolResponse(ResolvedClientTarget target,
                                      boolean explicitConnection,
                                      String protocolId,
                                      Status status,
                                      ClientResponseHeaders headers,
                                      Instant receivedAt,
                                      UriAuthority alternativeAuthority) {
        this.target = Objects.requireNonNull(target, "target");
        this.explicitConnection = explicitConnection;
        this.protocolId = requireProtocolId(protocolId);
        this.status = Objects.requireNonNull(status, "status");
        this.headers = new SparseClientResponseHeaders(Objects.requireNonNull(headers, "headers"));
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        this.alternativeAuthority = alternativeAuthority;
    }

    /**
     * Create response context for a request sent to its direct route.
     *
     * @param target resolved physical and logical target
     * @param explicitConnection whether the request used a caller-supplied or otherwise out-of-band connection
     * @param protocolId actual response protocol identifier
     * @param status response status
     * @param headers response headers
     * @param receivedAt time the response headers were received
     * @return response context
     */
    public static WebClientProtocolResponse create(ResolvedClientTarget target,
                                                   boolean explicitConnection,
                                                   String protocolId,
                                                   Status status,
                                                   ClientResponseHeaders headers,
                                                   Instant receivedAt) {
        return new WebClientProtocolResponse(target,
                                             explicitConnection,
                                             protocolId,
                                             status,
                                             headers,
                                             receivedAt,
                                             null);
    }

    /**
     * Create response context for a request sent to an alternative service.
     *
     * @param target resolved physical and logical target
     * @param explicitConnection whether the request used a caller-supplied or otherwise out-of-band connection
     * @param protocolId actual response protocol identifier
     * @param status response status
     * @param headers response headers
     * @param receivedAt time the response headers were received
     * @param alternativeAuthority alternative authority used for the request
     * @return response context
     */
    public static WebClientProtocolResponse createAlternative(ResolvedClientTarget target,
                                                              boolean explicitConnection,
                                                              String protocolId,
                                                              Status status,
                                                              ClientResponseHeaders headers,
                                                              Instant receivedAt,
                                                              UriAuthority alternativeAuthority) {
        return new WebClientProtocolResponse(target,
                                             explicitConnection,
                                             protocolId,
                                             status,
                                             headers,
                                             receivedAt,
                                             Objects.requireNonNull(alternativeAuthority, "alternativeAuthority"));
    }

    /**
     * Exact resolved target used for this response.
     *
     * @return resolved target
     */
    public ResolvedClientTarget target() {
        return target;
    }

    /**
     * Whether the request used a caller-supplied or otherwise out-of-band connection.
     *
     * @return whether the request used an explicit connection
     */
    public boolean explicitConnection() {
        return explicitConnection;
    }

    /**
     * Actual protocol selected for this response.
     *
     * @return protocol identifier
     */
    public String protocolId() {
        return protocolId;
    }

    /**
     * Response status.
     *
     * @return response status
     */
    public Status status() {
        return status;
    }

    /**
     * Full immutable snapshot of response headers.
     *
     * @return response headers
     */
    public ClientResponseHeaders headers() {
        return headers;
    }

    /**
     * Whether this response used TLS for an HTTPS target.
     *
     * @return whether the response is secure
     */
    public boolean secure() {
        ClientConnectionTarget logicalTarget = target.logicalTarget();
        return "https".equals(logicalTarget.scheme()) && logicalTarget.connectionKey().tls().enabled();
    }

    /**
     * Time the response headers were received.
     *
     * @return receipt time
     */
    public Instant receivedAt() {
        return receivedAt;
    }

    /**
     * Alternative authority used for this request, if any.
     *
     * @return alternative authority
     */
    public Optional<UriAuthority> alternativeAuthority() {
        return Optional.ofNullable(alternativeAuthority);
    }

    private static String requireProtocolId(String protocolId) {
        String id = Objects.requireNonNull(protocolId, "protocolId");
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Protocol identifier must not be empty");
        }
        return id;
    }

    private static final class SparseClientResponseHeaders implements ClientResponseHeaders {
        private final Header[] headers;

        private SparseClientResponseHeaders(ClientResponseHeaders source) {
            Header[] copied = new Header[source.size()];
            int index = 0;
            for (Header header : source) {
                if (index == copied.length) {
                    copied = Arrays.copyOf(copied, Math.max(1, index * 2));
                }
                copied[index++] = new SnapshotHeader(header);
            }
            this.headers = index == copied.length ? copied : Arrays.copyOf(copied, index);
        }

        @Override
        public List<String> all(HeaderName name, Supplier<List<String>> defaultSupplier) {
            Header header = findOrNull(name);
            return header == null ? defaultSupplier.get() : header.allValues();
        }

        @Override
        public boolean contains(HeaderName name) {
            return findOrNull(name) != null;
        }

        @Override
        public boolean contains(Header headerWithValue) {
            Header header = findOrNull(headerWithValue.headerName());
            if (header == null) {
                return false;
            }
            if (headerWithValue.valueCount() == 1 && header.valueCount() == 1) {
                return headerWithValue.get().equals(header.get());
            }
            return headerWithValue.allValues().equals(header.allValues());
        }

        @Override
        public Header get(HeaderName name) {
            Header header = findOrNull(name);
            if (header == null) {
                throw new NoSuchElementException("Header " + name + " is not present in these headers");
            }
            return header;
        }

        @Override
        public int size() {
            return headers.length;
        }

        @Override
        public List<HttpMediaType> acceptedTypes() {
            Header accept = findOrNull(HeaderNames.ACCEPT);
            if (accept == null) {
                return List.of();
            }

            List<String> accepts = accept.allValues(true);
            List<HttpMediaType> mediaTypes = new ArrayList<>(accepts.size());
            for (String value : accepts) {
                mediaTypes.add(HttpMediaType.create(value));
            }
            mediaTypes.sort(null);
            return List.copyOf(mediaTypes);
        }

        @Override
        public Iterator<Header> iterator() {
            return new Iterator<>() {
                private int index;

                @Override
                public boolean hasNext() {
                    return index < headers.length;
                }

                @Override
                public Header next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return headers[index++];
                }
            };
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            for (Header header : headers) {
                for (String value : header.allValues()) {
                    builder.append(header.name())
                            .append(": ");
                    if (header.sensitive()) {
                        builder.append("*".repeat(value.length()));
                    } else {
                        builder.append(value);
                    }
                    if (header.sensitive() || header.changing()) {
                        builder.append(" (");
                        if (header.sensitive()) {
                            builder.append("sensitive");
                            if (header.changing()) {
                                builder.append(", ");
                            }
                        }
                        if (header.changing()) {
                            builder.append("changing");
                        }
                        builder.append(")");
                    }
                    builder.append("\n");
                }
            }
            return builder.toString();
        }

        private Header findOrNull(HeaderName name) {
            String lowerCase = Objects.requireNonNull(name, "name").lowerCase();
            for (Header header : headers) {
                HeaderName candidate = header.headerName();
                if (candidate == name || candidate.lowerCase().equals(lowerCase)) {
                    return header;
                }
            }
            return null;
        }
    }

    private static final class SnapshotHeader implements Header {
        private static final String[] QUALIFIER = new String[] {"http", "header"};

        private final HeaderName headerName;
        private final String name;
        private final String firstValue;
        private final boolean sensitive;
        private final boolean changing;
        private volatile List<String> values;

        private SnapshotHeader(Header source) {
            this.headerName = source.headerName();
            this.name = source.name();
            this.firstValue = source.get();
            if (source.valueCount() > 1) {
                this.values = List.copyOf(source.allValues());
            }
            this.sensitive = source.sensitive();
            this.changing = source.changing();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public HeaderName headerName() {
            return headerName;
        }

        @Override
        public String get() {
            return firstValue;
        }

        @Override
        public String getString() {
            return get();
        }

        @Override
        public List<String> allValues() {
            List<String> result = values;
            if (result == null) {
                result = List.of(firstValue);
                values = result;
            }
            return result;
        }

        @Override
        public int valueCount() {
            List<String> result = values;
            return result == null ? 1 : result.size();
        }

        @Override
        public boolean sensitive() {
            return sensitive;
        }

        @Override
        public boolean changing() {
            return changing;
        }

        @Override
        public <N> Value<N> as(Class<N> type) {
            return value().as(type);
        }

        @Override
        public <N> Value<N> as(GenericType<N> type) {
            return value().as(type);
        }

        @Override
        public <N> Value<N> as(Function<? super String, ? extends N> mapper) {
            return value().as(mapper);
        }

        @Override
        public Optional<String> asOptional() {
            return Optional.of(get());
        }

        @Override
        public Value<Boolean> asBoolean() {
            return value().asBoolean();
        }

        @Override
        public Value<String> asString() {
            return value();
        }

        @Override
        public Value<Integer> asInt() {
            return value().asInt();
        }

        @Override
        public Value<Long> asLong() {
            return value().asLong();
        }

        @Override
        public Value<Double> asDouble() {
            return value().asDouble();
        }

        private Value<String> value() {
            return Value.create(name, get(), QUALIFIER);
        }
    }
}
