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
    private static final int INVALID_Q = -1;
    private static final int Q_ZERO = 0;
    private static final int Q_ONE = 1000;
    private static final AcceptEncoding ABSENT = new AcceptEncoding(false, true, Map.of(), null);

    private final boolean present;
    private final boolean valid;
    private final Map<String, Entry> entries;
    private final Entry wildcard;
    private volatile List<CodingQuality> acceptedCodings;
    private volatile List<CodingQuality> acceptedCodingsWithWildcard;

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
            return ABSENT;
        }

        Map<String, Entry> entries = new HashMap<>();
        Entry wildcard = null;
        boolean valid = true;
        int order = 0;

        for (String headerValue : headers.get(HeaderNames.ACCEPT_ENCODING).allValues()) {
            int start = 0;
            int length = headerValue.length();
            for (int i = 0; i <= length; i++) {
                if (i != length && headerValue.charAt(i) != ',') {
                    continue;
                }
                int valueStart = skipWhitespace(headerValue, start, i);
                int valueEnd = trimWhitespace(headerValue, valueStart, i);
                start = i + 1;
                if (valueStart == valueEnd) {
                    continue;
                }
                Entry entry = parse(headerValue, valueStart, valueEnd, order++);
                if (entry == null) {
                    valid = false;
                    continue;
                }

                if (WILDCARD.equals(entry.coding())) {
                    wildcard = better(wildcard, entry);
                } else {
                    entries.put(entry.coding(), better(entries.get(entry.coding()), entry));
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
     * Match a content coding.
     *
     * @param coding concrete or wildcard coding
     * @param wildcardAllowed whether wildcard may match the coding
     * @return coding quality if the coding is acceptable, otherwise empty
     */
    public Optional<CodingQuality> match(String coding, boolean wildcardAllowed) {
        Objects.requireNonNull(coding);
        String normalized = normalize(coding);
        if (IDENTITY.equals(normalized)) {
            return identity();
        }
        if (!present) {
            return Optional.of(new FullCodingQuality(normalized, Q_ONE, IMPLICIT_IDENTITY_ORDER, false));
        }
        Entry specific = entries.get(normalized);
        if (specific != null) {
            return specific.q() > Q_ZERO
                    ? Optional.of(new EntryCodingQuality(specific, false))
                    : Optional.empty();
        }
        if (wildcardAllowed && wildcard != null && wildcard.q() > Q_ZERO) {
            return Optional.of(new FullCodingQuality(normalized, wildcard.q(), wildcard.order(), true));
        }
        return Optional.empty();
    }

    /**
     * Match identity.
     *
     * @return coding quality if identity is acceptable
     */
    public Optional<CodingQuality> identity() {
        Entry identity = entries.get(IDENTITY);
        if (identity != null) {
            return identity.q() > Q_ZERO
                    ? Optional.of(new EntryCodingQuality(identity, false))
                    : Optional.empty();
        }
        if (wildcard != null && wildcard.q() == Q_ZERO) {
            return Optional.empty();
        }
        return Optional.of(new FullCodingQuality(IDENTITY, Q_ONE, IMPLICIT_IDENTITY_ORDER, false));
    }

    /**
     * Accepted non-identity coding qualities from this header.
     *
     * @param wildcardAllowed whether wildcard should be included
     * @return immutable accepted non-identity coding qualities
     */
    public List<CodingQuality> acceptedCodings(boolean wildcardAllowed) {
        if (wildcardAllowed) {
            if (wildcard == null || wildcard.q() <= Q_ZERO) {
                return acceptedCodings(false);
            }

            List<CodingQuality> result = acceptedCodingsWithWildcard;
            if (result == null) {
                List<CodingQuality> noWildcard = acceptedCodings;
                if (noWildcard == null) {
                    noWildcard = createAcceptedCodings();
                    acceptedCodings = noWildcard;
                }
                result = createAcceptedCodingsWithWildcard(noWildcard);
                acceptedCodingsWithWildcard = result;
            }
            return result;
        }

        List<CodingQuality> result = acceptedCodings;
        if (result == null) {
            result = createAcceptedCodings();
            acceptedCodings = result;
        }
        return result;
    }

    private List<CodingQuality> createAcceptedCodings() {
        List<CodingQuality> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (!IDENTITY.equals(entry.coding()) && entry.q() > Q_ZERO) {
                result.add(new EntryCodingQuality(entry, false));
            }
        }
        if (result.isEmpty()) {
            return List.of();
        }
        result.sort(AcceptEncoding::compare);
        return List.copyOf(result);
    }

    private List<CodingQuality> createAcceptedCodingsWithWildcard(List<CodingQuality> noWildcard) {
        List<CodingQuality> result = new ArrayList<>(noWildcard.size() + 1);
        result.addAll(noWildcard);
        result.add(new EntryCodingQuality(wildcard, true));
        result.sort(AcceptEncoding::compare);
        return List.copyOf(result);
    }

    /**
     * Select the best runtime encoding from the available codings.
     * <p>
     * Equal-quality concrete codings use the client header order, with the provided coding order as a fallback for
     * wildcard and otherwise equivalent matches.
     *
     * @param codings available concrete codings in fallback order
     * @return best coding, identity, or empty if none is acceptable
     */
    public Optional<CodingQuality> best(List<String> codings) {
        Objects.requireNonNull(codings);
        Entry identity = entries.get(IDENTITY);
        int identityQ = Q_ONE;
        int identityOrder = IMPLICIT_IDENTITY_ORDER;
        boolean identityAccepted = true;
        if (identity != null) {
            identityQ = identity.q();
            identityOrder = identity.order();
            identityAccepted = identityQ > Q_ZERO;
        } else if (wildcard != null && wildcard.q() == Q_ZERO) {
            identityAccepted = false;
        }

        String bestCoding = null;
        Entry bestEntry = null;
        BestCandidate bestCandidate = null;
        boolean identityLosesEqualQuality = false;
        for (int i = 0; i < codings.size(); i++) {
            String coding = normalize(codings.get(i));
            Entry entry = null;
            int q;
            int order;
            boolean wildcardMatch = false;
            if (IDENTITY.equals(coding)) {
                if (!identityAccepted) {
                    continue;
                }
                entry = identity;
                q = identityQ;
                order = identityOrder;
            } else if (!present) {
                q = Q_ONE;
                order = IMPLICIT_IDENTITY_ORDER;
            } else {
                entry = entries.get(coding);
                if (entry != null) {
                    q = entry.q();
                    order = entry.order();
                } else if (wildcard != null) {
                    q = wildcard.q();
                    order = wildcard.order();
                    wildcardMatch = true;
                } else {
                    continue;
                }
                if (q <= Q_ZERO) {
                    continue;
                }
            }

            if (identityAccepted
                    && identityOrder != IMPLICIT_IDENTITY_ORDER
                    && q == identityQ
                    && identityOrder > order) {
                identityLosesEqualQuality = true;
            }
            BestCandidate candidate = new BestCandidate(q, wildcardMatch, order, i);
            if (bestCoding == null || betterBestCoding(candidate, bestCandidate)) {
                bestCoding = coding;
                bestEntry = entry;
                bestCandidate = candidate;
            }
        }

        if (!identityAccepted) {
            return bestCoding == null
                    ? Optional.empty()
                    : Optional.of(codingQuality(bestCoding, bestEntry, bestCandidate.q(),
                                               bestCandidate.order(), bestCandidate.wildcard()));
        }
        if (bestCoding == null) {
            return Optional.of(identityQuality(identity));
        }

        int q = Integer.compare(bestCandidate.q(), identityQ);
        if (q > 0) {
            return Optional.of(codingQuality(bestCoding, bestEntry, bestCandidate.q(),
                                           bestCandidate.order(), bestCandidate.wildcard()));
        }
        if (q < 0 || (identityOrder != IMPLICIT_IDENTITY_ORDER && !identityLosesEqualQuality)) {
            return Optional.of(identityQuality(identity));
        }
        return Optional.of(codingQuality(bestCoding, bestEntry, bestCandidate.q(),
                                       bestCandidate.order(), bestCandidate.wildcard()));
    }

    private static Entry parse(String value, int start, int end, int order) {
        int semicolon = indexOf(value, ';', start, end);
        int codingStart = start;
        int codingEnd = semicolon == -1 ? end : semicolon;
        codingStart = skipWhitespace(value, codingStart, codingEnd);
        codingEnd = trimWhitespace(value, codingStart, codingEnd);

        if (codingStart == codingEnd) {
            return null;
        }
        // RFC 9110, sections 12.5.3 and 8.4.1: codings are content-coding tokens, identity, or *.
        String coding = normalize(value, codingStart, codingEnd);
        try {
            HttpToken.validate(coding);
        } catch (IllegalArgumentException e) {
            return null;
        }

        int q = Q_ONE;
        if (semicolon != -1) {
            // RFC 9110, sections 12.5.3 and 12.4.2: Accept-Encoding permits only codings [ weight ].
            int parameterStart = semicolon + 1;
            if (indexOf(value, ';', parameterStart, end) != -1) {
                return null;
            }
            parameterStart = skipWhitespace(value, parameterStart, end);
            int parameterEnd = trimWhitespace(value, parameterStart, end);
            if (parameterStart == parameterEnd
                    || parameterEnd - parameterStart < 3
                    || value.charAt(parameterStart + 1) != '=') {
                return null;
            }
            char name = value.charAt(parameterStart);
            if (name != 'q' && name != 'Q') {
                return null;
            }
            q = parseQvalue(value, parameterStart + 2, parameterEnd);
            if (q == INVALID_Q) {
                return null;
            }
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

    private static int compare(CodingQuality first, CodingQuality second) {
        int result = compareQuality(first, second);
        if (result != 0) {
            return result;
        }
        return Integer.compare(first.order(), second.order());
    }

    private static boolean betterBestCoding(BestCandidate candidate, BestCandidate current) {
        if (candidate.q() != current.q()) {
            return candidate.q() > current.q();
        }
        if (candidate.wildcard() != current.wildcard()) {
            return !candidate.wildcard();
        }
        if (candidate.order() != current.order()) {
            return candidate.order() < current.order();
        }
        return candidate.serverOrder() < current.serverOrder();
    }

    private static int compareQuality(CodingQuality first, CodingQuality second) {
        int q = Integer.compare(qValue(second), qValue(first));
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

        return 0;
    }

    private static CodingQuality identityQuality(Entry identity) {
        if (identity == null) {
            return new FullCodingQuality(IDENTITY, Q_ONE, IMPLICIT_IDENTITY_ORDER, false);
        }
        return new EntryCodingQuality(identity, false);
    }

    private static CodingQuality codingQuality(String coding, Entry entry, int q, int order, boolean wildcard) {
        if (entry != null && !wildcard) {
            return new EntryCodingQuality(entry, false);
        }
        return new FullCodingQuality(coding, q, order, wildcard);
    }

    private static boolean codingQualityEquals(CodingQuality first, Object second) {
        if (first == second) {
            return true;
        }
        if (!(second instanceof CodingQuality that)) {
            return false;
        }
        return qValue(first) == qValue(that)
                && first.order() == that.order()
                && first.wildcard() == that.wildcard()
                && first.coding().equals(that.coding());
    }

    private static int codingQualityHashCode(CodingQuality quality) {
        int result = quality.coding().hashCode();
        result = 31 * result + qValue(quality);
        result = 31 * result + quality.order();
        result = 31 * result + Boolean.hashCode(quality.wildcard());
        return result;
    }

    private static String normalize(String coding) {
        return coding.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value, int start, int end) {
        return value.substring(start, end).toLowerCase(Locale.ROOT);
    }

    private static int parseQvalue(String value, int start, int end) {
        // RFC 9110, section 12.4.2: qvalue is 0 or 1 with up to three fractional digits.
        int length = end - start;
        if (length == 0) {
            return INVALID_Q;
        }
        char first = value.charAt(start);
        if (first != '0' && first != '1') {
            return INVALID_Q;
        }
        if (length == 1) {
            return first == '1' ? Q_ONE : Q_ZERO;
        }
        if (value.charAt(start + 1) != '.' || length > 5) {
            return INVALID_Q;
        }
        int result = Q_ZERO;
        int multiplier = 100;
        for (int i = start + 2; i < end; i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return INVALID_Q;
            }
            int digit = c - '0';
            if (first == '1') {
                if (digit != 0) {
                    return INVALID_Q;
                }
            } else {
                result += digit * multiplier;
                multiplier /= 10;
            }
        }
        return first == '1' ? Q_ONE : result;
    }

    private static int indexOf(String value, char c, int start, int end) {
        for (int i = start; i < end; i++) {
            if (value.charAt(i) == c) {
                return i;
            }
        }
        return -1;
    }

    private static int skipWhitespace(String value, int start, int end) {
        int result = start;
        while (result < end && value.charAt(result) <= ' ') {
            result++;
        }
        return result;
    }

    private static int trimWhitespace(String value, int start, int end) {
        int result = end;
        while (result > start && value.charAt(result - 1) <= ' ') {
            result--;
        }
        return result;
    }

    private static int qValue(CodingQuality quality) {
        if (quality instanceof EntryCodingQuality entryQuality) {
            return entryQuality.entry.q();
        }
        if (quality instanceof FullCodingQuality fullQuality) {
            return fullQuality.q;
        }
        return (int) Math.round(quality.q() * Q_ONE);
    }

    private static double qAsDouble(int q) {
        return q / (double) Q_ONE;
    }

    /**
     * Content coding with its effective quality.
     */
    public interface CodingQuality {
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

    private static final class EntryCodingQuality implements CodingQuality {
        private final Entry entry;
        private final boolean wildcard;

        private EntryCodingQuality(Entry entry, boolean wildcard) {
            this.entry = entry;
            this.wildcard = wildcard;
        }

        @Override
        public String coding() {
            return entry.coding();
        }

        @Override
        public double q() {
            return qAsDouble(entry.q());
        }

        @Override
        public int order() {
            return entry.order();
        }

        @Override
        public boolean wildcard() {
            return wildcard;
        }

        @Override
        public boolean equals(Object obj) {
            return codingQualityEquals(this, obj);
        }

        @Override
        public int hashCode() {
            return codingQualityHashCode(this);
        }
    }

    private static final class FullCodingQuality implements CodingQuality {
        private final String coding;
        private final int q;
        private final int order;
        private final boolean wildcard;

        private FullCodingQuality(String coding, int q, int order, boolean wildcard) {
            this.coding = Objects.requireNonNull(coding);
            if (q < Q_ZERO || q > Q_ONE) {
                throw new IllegalArgumentException("q value must be within 0 and 1: " + q);
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
            return qAsDouble(q);
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public boolean wildcard() {
            return wildcard;
        }

        @Override
        public boolean equals(Object obj) {
            return codingQualityEquals(this, obj);
        }

        @Override
        public int hashCode() {
            return codingQualityHashCode(this);
        }
    }

    private record BestCandidate(int q, boolean wildcard, int order, int serverOrder) {
    }

    private record Entry(String coding, int q, int order) {
    }

}
