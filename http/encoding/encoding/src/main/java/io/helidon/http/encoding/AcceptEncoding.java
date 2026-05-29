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

package io.helidon.http.encoding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpToken;

/**
 * Parsed {@code Accept-Encoding} header values and quality matching.
 * <p>
 * <b>This is NOT part of any supported API. If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or deletion without notice.</b>
 */
@Api.Internal
public final class AcceptEncoding {
    /**
     * Identity coding.
     */
    public static final String IDENTITY = "identity";

    /**
     * Wildcard coding.
     */
    public static final String WILDCARD = "*";

    private static final int IMPLICIT_IDENTITY_ORDER = Integer.MAX_VALUE;

    private final boolean present;
    private final boolean valid;
    private final Map<String, Entry> entries;
    private final Entry wildcard;

    private AcceptEncoding(boolean present, boolean valid, Map<String, Entry> entries, Entry wildcard) {
        this.present = present;
        this.valid = valid;
        this.entries = entries;
        this.wildcard = wildcard;
    }

    /**
     * Parse {@code Accept-Encoding} from headers.
     *
     * @param headers headers
     * @return parsed accept encoding
     */
    public static AcceptEncoding create(Headers headers) {
        Objects.requireNonNull(headers);
        if (!headers.contains(HeaderNames.ACCEPT_ENCODING)) {
            return new AcceptEncoding(false, true, Map.of(), null);
        }

        Map<String, Entry> entries = new HashMap<>();
        Entry wildcard = null;
        boolean valid = true;
        int order = 0;

        for (String headerValue : headers.get(HeaderNames.ACCEPT_ENCODING).allValues()) {
            String[] parts = headerValue.split(",");
            for (String part : parts) {
                String value = part.trim();
                if (value.isEmpty()) {
                    continue;
                }
                Entry entry = parse(value, order++);
                if (entry == null) {
                    valid = false;
                    continue;
                }

                if (WILDCARD.equals(entry.coding())) {
                    wildcard = better(wildcard, entry);
                } else {
                    entries.compute(entry.coding(), (_, current) -> better(current, entry));
                }
            }
        }

        return new AcceptEncoding(true, valid, Map.copyOf(entries), wildcard);
    }

    /**
     * Whether the header was present.
     *
     * @return whether {@code Accept-Encoding} was present
     */
    public boolean present() {
        return present;
    }

    /**
     * Whether parsing did not encounter malformed values.
     *
     * @return whether parsed values are valid
     */
    public boolean valid() {
        return valid;
    }

    /**
     * Match a concrete content coding.
     *
     * @param coding coding
     * @param wildcardAllowed whether wildcard may match the coding
     * @return quality if the coding is acceptable
     */
    public Optional<Quality> match(String coding, boolean wildcardAllowed) {
        Objects.requireNonNull(coding);
        String normalized = normalize(coding);
        if (IDENTITY.equals(normalized)) {
            return identity();
        }
        if (!present) {
            return Optional.of(new FullQuality(normalized, 1, IMPLICIT_IDENTITY_ORDER, false));
        }
        Entry specific = entries.get(normalized);
        if (specific != null) {
            return specific.q() > 0
                    ? Optional.of(new EntryQuality(specific, false))
                    : Optional.empty();
        }
        if (wildcardAllowed && wildcard != null && wildcard.q() > 0) {
            return Optional.of(new FullQuality(normalized, wildcard.q(), wildcard.order(), true));
        }
        return Optional.empty();
    }

    /**
     * Match identity.
     *
     * @return quality if identity is acceptable
     */
    public Optional<Quality> identity() {
        Entry identity = entries.get(IDENTITY);
        if (identity != null) {
            return identity.q() > 0
                    ? Optional.of(new EntryQuality(identity, false))
                    : Optional.empty();
        }
        if (wildcard != null && wildcard.q() == 0) {
            return Optional.empty();
        }
        return Optional.of(new FullQuality(IDENTITY, 1, IMPLICIT_IDENTITY_ORDER, false));
    }

    /**
     * Accepted non-identity coding qualities from this header.
     *
     * @param wildcardAllowed whether wildcard should be included
     * @return accepted non-identity coding qualities
     */
    public List<Quality> acceptedCodings(boolean wildcardAllowed) {
        List<Quality> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (!IDENTITY.equals(entry.coding()) && entry.q() > 0) {
                result.add(new EntryQuality(entry, false));
            }
        }
        if (wildcardAllowed && wildcard != null && wildcard.q() > 0) {
            result.add(new EntryQuality(wildcard, true));
        }
        result.sort(AcceptEncoding::compare);
        return result;
    }

    /**
     * Select the best runtime encoding from the available codings.
     *
     * @param codings available concrete codings in server preference order
     * @return best coding, or identity
     */
    public Optional<Quality> best(List<String> codings) {
        List<Quality> candidates = new ArrayList<>();
        identity().ifPresent(candidates::add);
        for (String coding : codings) {
            match(coding, true).ifPresent(candidates::add);
        }
        return candidates.stream()
                .min(AcceptEncoding::compare);
    }

    private static Entry parse(String value, int order) {
        String[] parts = value.split(";", -1);
        String coding = normalize(parts[0]);
        if (coding.isEmpty()) {
            return null;
        }
        // RFC 9110, sections 12.5.3 and 8.4.1: codings are content-coding tokens, identity, or *.
        try {
            HttpToken.validate(coding);
        } catch (IllegalArgumentException e) {
            return null;
        }

        double q = 1;
        for (int i = 1; i < parts.length; i++) {
            // RFC 9110, sections 12.5.3 and 12.4.2: Accept-Encoding permits only codings [ weight ].
            String parameter = parts[i].trim();
            if (parameter.isEmpty()) {
                return null;
            }
            if (i != 1) {
                return null;
            }
            if (parameter.length() < 3 || parameter.charAt(1) != '=') {
                return null;
            }
            char name = parameter.charAt(0);
            if (name != 'q' && name != 'Q') {
                return null;
            }
            Optional<Double> parsed = parseQvalue(parameter.substring(2));
            if (parsed.isEmpty()) {
                return null;
            }
            q = parsed.get();
        }
        return new Entry(coding, q, order);
    }

    private static Entry better(Entry current, Entry candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate.q() > current.q()) {
            return candidate;
        }
        if (candidate.q() == current.q() && candidate.order() < current.order()) {
            return candidate;
        }
        return current;
    }

    private static int compare(Quality first, Quality second) {
        int q = Double.compare(second.q(), first.q());
        if (q != 0) {
            return q;
        }

        boolean firstImplicitIdentity = IDENTITY.equals(first.coding()) && first.order() == IMPLICIT_IDENTITY_ORDER;
        boolean secondImplicitIdentity = IDENTITY.equals(second.coding()) && second.order() == IMPLICIT_IDENTITY_ORDER;
        if (firstImplicitIdentity != secondImplicitIdentity) {
            return firstImplicitIdentity ? 1 : -1;
        }

        if (first.wildcard() != second.wildcard()) {
            return first.wildcard() ? 1 : -1;
        }

        if (IDENTITY.equals(first.coding())) {
            return -1;
        }
        if (IDENTITY.equals(second.coding())) {
            return 1;
        }

        if (first.order() != second.order()) {
            return Integer.compare(first.order(), second.order());
        }
        return 0;
    }

    private static String normalize(String coding) {
        return coding.trim().toLowerCase(Locale.ROOT);
    }

    private static Optional<Double> parseQvalue(String value) {
        // RFC 9110, section 12.4.2: qvalue is 0 or 1 with up to three fractional digits.
        if (value.isEmpty()) {
            return Optional.empty();
        }
        int length = value.length();
        char first = value.charAt(0);
        if (first != '0' && first != '1') {
            return Optional.empty();
        }
        if (length == 1) {
            return Optional.of((double) (first - '0'));
        }
        if (value.charAt(1) != '.' || length > 5) {
            return Optional.empty();
        }
        for (int i = 2; i < length; i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return Optional.empty();
            }
            if (first == '1' && c != '0') {
                return Optional.empty();
            }
        }
        return Optional.of(Double.parseDouble(value));
    }

    /**
     * Effective quality of a coding.
     */
    public interface Quality {
        /**
         * Content coding.
         *
         * @return content coding
         */
        String coding();
        /**
         * Quality.
         *
         * @return quality
         */
        double q();
        /**
         * Source order.
         *
         * @return source order
         */
        int order();

        /**
         * Whether the coding matched through wildcard.
         *
         * @return whether wildcard matched this coding
         */
        boolean wildcard();
    }

    private static final class EntryQuality implements Quality {
        private final Entry entry;
        private final boolean wildcard;

        private EntryQuality(Entry entry, boolean wildcard) {
            this.entry = entry;
            this.wildcard = wildcard;
        }

        @Override
        public String coding() {
            return entry.coding();
        }

        @Override
        public double q() {
            return entry.q();
        }

        @Override
        public int order() {
            return entry.order();
        }

        @Override
        public boolean wildcard() {
            return wildcard;
        }
    }

    private static final class FullQuality implements Quality {
        private final String coding;
        private final double q;
        private final int order;
        private final boolean wildcard;

        private FullQuality(String coding, double q, int order, boolean wildcard) {
            this.coding = Objects.requireNonNull(coding);
            if (!Double.isFinite(q) || q < 0 || q > 1) {
                throw new IllegalArgumentException("Quality must be within 0 and 1: " + q);
            }
            this.q = q;
            this.order = order;
            this.wildcard = wildcard;
        }

        @Override
        public String coding() {
            return coding;
        }

        @Override
        public double q() {
            return q;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public boolean wildcard() {
            return wildcard;
        }
    }

    private record Entry(String coding, double q, int order) {
    }

}
