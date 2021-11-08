/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Support for Web Server path pattern compilation and matching.
 *
 * @see PathMatcher
 */
final class PathPattern {

    static final PathMatcher.PrefixResult NOT_MATCHED_RESULT = new PathMatcher.PrefixResult() {
        @Override
        public String remainingPart() {
            return null;
        }

        @Override
        public boolean matches() {
            return false;
        }

        @Override
        public Map<String, String> params() {
            return Collections.emptyMap();
        }

        @Override
        public String param(String name) {
            return null;
        }
    };

    private static final char[] REGEXP_META_CHARACTERS = "<([{\\^-=$!|]})?*+.>".toCharArray();
    private static final String PARAM_PREFIX = "gfXdbHQlk";

    static {
        // The REGEXP_META_CHARACTERS are used in binary search. IT MUST BE SORTED!
        Arrays.sort(REGEXP_META_CHARACTERS);
    }

    /**
     * A utility class. Cannot be constructed.
     */
    private PathPattern() {
    }

    /**
     * Compiles a standard {@link PathMatcher} pattern.
     *
     * @param pattern a pattern to from.
     * @return Compiled path pattern matcher.
     * @throws NullPointerException if parameter pattern is {@code null}.
     * @throws IllegalPathPatternException if pattern cannot be compiled.
     */
    static PathMatcher compile(CharSequence pattern) {
        Objects.requireNonNull(pattern, "Parameter 'pattern' is null!");

        StringBuilder regexp = new StringBuilder(pattern.length() * 2);
        StringBuilder canonical = new StringBuilder(pattern.length());
        boolean isRegexp = false;
        boolean escape = false;
        boolean optionalSequence = false;
        int paramCounter = 0;
        Map<String, String> paramToGroupName = new HashMap<>();

        CharIterator iter = new CharIterator(pattern);
        while (iter.hasNext()) {
            char ch = iter.next();
            // Process special characters
            if (escape) {
                escape = false;
            } else {
                boolean shouldContinue = true;
                switch (ch) {
                case '\\':
                    escape = true;
                    break;
                case '[':
                    if (optionalSequence) {
                        throw new IllegalPathPatternException("Optional sequences cannot be nested!",
                                                              pattern.toString(),
                                                              iter.index() - 1);
                    } else {
                        optionalSequence = true;
                        isRegexp = true;
                        regexp.append('(');
                    }
                    break;
                case ']':
                    if (optionalSequence) {
                        optionalSequence = false;
                        regexp.append(")?");
                    } else {
                        shouldContinue = false; // Closing bracket is generally fine
                    }
                    break;
                case '{':
                    isRegexp = true;
                    String name = parseParameter(iter, regexp, paramCounter);
                    if (name.length() > 0) {
                        paramToGroupName.put(name, PARAM_PREFIX + paramCounter);
                        paramCounter++;
                    }
                    break;
                case '*':
                    isRegexp = true;
                    regexp.append(".*?");
                    break;
                default:
                        shouldContinue = false;
                }
                if (shouldContinue) {
                    continue;
                }
            }
            escapeIfNeeded(ch, regexp);
            canonical.append(ch);
        }
        // Build result
        if (optionalSequence) {
            throw new IllegalPathPatternException("Missing end of a optional sequence (']' character)!",
                                                  pattern.toString(),
                                                  iter.index() - 1);
        }
        try {
            if (isRegexp) {
                return new RegexpPathMatcher(regexp.toString(), paramToGroupName);
            } else {
                return new CanonicalPathMatcher(canonical.toString());
            }
        } catch (RuntimeException e) {
            throw new IllegalPathPatternException("Cannot parse generated regular expression!", pattern.toString(), 0);
        }
    }

    private static void escapeIfNeeded(char ch, StringBuilder builder) {
        if (Arrays.binarySearch(REGEXP_META_CHARACTERS, ch) < 0) {
            builder.append(ch);
        } else {
            builder.append('\\').append(ch);
        }
    }

    private static String parseParameter(CharIterator iter, StringBuilder builder, int index) {
        StringBuilder name = new StringBuilder();
        boolean first = true;
        boolean greedy = false;
        while (iter.hasNext()) {
            char ch = iter.next();
            // First character modifier
            if (first) {
                first = false;
                if ('+' == ch) {
                    greedy = true;
                    continue;
                }
            }
            switch (ch) {
            case ':':
                if (greedy) {
                    throw new IllegalPathPatternException("Parameter modifier '+' cannot be combined with custom regexp!",
                                                          iter.seq.toString(),
                                                          iter.index() - 1);
                }
                String r1 = name.toString().trim();
                addParamRegexp(builder, r1.length() > 0 ? index : -1, parseParamRegexp(iter));
                return r1;
            case '}':
                String r2 = name.toString().trim();
                addParamRegexp(builder,
                               r2.length() > 0 ? index : -1,
                               greedy ? ".+" : "[^/]+");
                return r2;
            default:
                name.append(ch);
            }
        }
        throw new IllegalPathPatternException("Pattern parameter has no end character '}'!",
                                              iter.seq.toString(),
                                              iter.index() - 1);
    }

    private static String parseParamRegexp(CharIterator iter) {
        StringBuilder builder = new StringBuilder();
        int subSeqCounter = 0;
        while (iter.hasNext()) {
            char ch = iter.next();
            switch (ch) {
            case '{':
                subSeqCounter++;
                break;
            case '}':
                if (subSeqCounter > 0) {
                    subSeqCounter--;
                } else {
                    return builder.toString();
                }
                break;
            default:
                builder.append(ch);
            }
        }
        throw new IllegalPathPatternException("Pattern parameter has specified regexp but no end character '}'!",
                                              iter.seq.toString(),
                                              iter.index() - 1);
    }

    private static String addParamRegexp(StringBuilder builder, int nameIndex, String regexp) {
        builder.append("(");
        if (nameIndex >= 0) {
            builder.append("?<").append(PARAM_PREFIX).append(nameIndex).append('>');
        }
        builder.append(regexp);
        builder.append(')');
        return builder.toString();
    }

    /**
     * Simple 'like iterator' API for char sequence.
     */
    private static class CharIterator {

        private final CharSequence seq;
        private int index = 0;

        /**
         * Creates new instance.
         *
         * @param seq Sequence to iterate.
         */
        CharIterator(CharSequence seq) {
            this.seq = seq;
        }

        /**
         * Returns {@code true} if sequence has next element.
         *
         * @return {@code true} if sequence has next element.
         */
        boolean hasNext() {
            return seq.length() > index;
        }

        /**
         * Returns next character or {@code u0000} if there is no more characters.
         *
         * @return next character.
         */
        char next() {
            if (hasNext()) {
                return seq.charAt(index++);
            } else {
                return '\u0000';
            }
        }

        /**
         * Returns an index in the current sequence.
         *
         * @return an index.
         */
        int index() {
            return index;
        }
    }

    /**
     * Path matcher using standard {@code String.equals()} and {@code String.startWith()} methods.
     */
    static class CanonicalPathMatcher implements PathMatcher {

        private final String pattern;

        /**
         * Creates new instance.
         *
         * @param pattern an exact pattern.
         * @throws NullPointerException  In case of {@code null} pattern parameter.
         */
        CanonicalPathMatcher(String pattern) {
            Objects.requireNonNull(pattern, "Parameter 'pattern' is null!");
            this.pattern = pattern;
        }

        @Override
        public Result match(CharSequence path) {
            Objects.requireNonNull(path, "Parameter 'path' is null!");
            if (path.equals(pattern)) {
                return new PositiveResult(null);
            } else {
                return NOT_MATCHED_RESULT;
            }
        }

        @Override
        public PrefixResult prefixMatch(CharSequence path) {
            Objects.requireNonNull(path, "Parameter 'path' is null!");
            String s = path.toString();
            if (s.startsWith(pattern)) {
                String rightPart = pattern.equals("/") ? s : s.substring(pattern.length());
                if (rightPart.isEmpty()) {
                    rightPart = "/";
                }
                if (rightPart.charAt(0) == '/') {
                    return new PositiveResult(null, rightPart);
                } else {
                    return NOT_MATCHED_RESULT;
                }
            } else {
                return NOT_MATCHED_RESULT;
            }
        }

        @Override
        public String toString() {
            return "CanonicalPathMatcher{"
                    + "pattern='" + pattern + '\''
                    + '}';
        }
    }

    /**
     * Regular expression based matcher.
     */
    static class RegexpPathMatcher implements PathMatcher {

        private static final String RIGHT_PART_PARAM_NAME = PARAM_PREFIX + "rightpart";

        private final Map<String, String> paramToGroupName;
        private final Pattern pattern;
        private final Pattern leftPattern;

        /**
         * Creates new instance.
         *
         * @param regexp an regular expression.
         * @param paramToGroupName a map of pattern parameter names and it's regexp matching group names.
         * @throws NullPointerException  In case of {@code null} regexp parameter.
         * @throws PatternSyntaxException If the expression's syntax is invalid.
         */
        RegexpPathMatcher(String regexp, Map<String, String> paramToGroupName) {
            Objects.requireNonNull(regexp, "Parameter 'pattern' is null!");
            this.pattern = Pattern.compile(regexp);
            this.leftPattern = Pattern.compile(regexp + "(?<" + RIGHT_PART_PARAM_NAME + ">/.+)?");
            if (paramToGroupName == null) {
                this.paramToGroupName = Collections.emptyMap();
            } else {
                this.paramToGroupName = new HashMap<>(paramToGroupName);
            }
        }

        @Override
        public Result match(CharSequence path) {
            Matcher matcher = pattern.matcher(path);
            if (matcher.matches()) {
                return new PositiveResult(exctractPatternParams(matcher));
            } else {
                return NOT_MATCHED_RESULT;
            }
        }

        @Override
        public PrefixResult prefixMatch(CharSequence path) {
            Matcher matcher = leftPattern.matcher(path);
            if (matcher.matches()) {
                String rightPart = matcher.group(RIGHT_PART_PARAM_NAME);
                if (rightPart == null || rightPart.isEmpty()) {
                    rightPart = "/";
                }
                if (rightPart.charAt(0) == '/') {
                    return new PositiveResult(exctractPatternParams(matcher), rightPart);
                } else {
                    return NOT_MATCHED_RESULT;
                }
            } else {
                return NOT_MATCHED_RESULT;
            }
        }

        private Map<String, String> exctractPatternParams(Matcher matcher) {
            // Old school, but slightly faster then stream.
            Map<String, String> params = new HashMap<>(paramToGroupName.size());
            for (Map.Entry<String, String> entry : paramToGroupName.entrySet()) {
                String paramValue = matcher.group(entry.getValue());
                if (paramValue != null) {
                    params.put(entry.getKey(), paramValue);
                }
            }
            return params;
        }

        @Override
        public String toString() {
            return "RegexpPathMatcher{"
                    + "pattern=" + pattern
                    + '}';
        }
    }

    /**
     * Represents ({@code matches == true}) positive result.
     */
    static class PositiveResult implements PathMatcher.PrefixResult {

        private final Map<String, String> params;
        private final String rightPart;

        /**
         * Creates new instance of positive ({@code matches == true}) result.
         *
         * @param params Resolved path parameters.
         * @param rightPart the reminder in case of a successful prefix match.
         */
        PositiveResult(Map<String, String> params, String rightPart) {
            if (params == null) {
                this.params = Collections.emptyMap();
            } else {
                this.params = Collections.unmodifiableMap(params);
            }
            if (rightPart == null) {
                this.rightPart = "";
            } else {
                this.rightPart = rightPart;
            }
        }

        /**
         * Creates new instance of positive ({@code matches == true}) result with empty {@code remainingPart}.
         * It can be used as implementation of {@link PathMatcher.Result PathMatcher.Result}.
         *
         * @param params Resolved path parameters.
         */
        PositiveResult(Map<String, String> params) {
            this(params, "");
        }

        @Override
        public boolean matches() {
            return true;
        }

        @Override
        public Map<String, String> params() {
            return params;
        }

        @Override
        public String param(String name) {
            return params.get(name);
        }

        @Override
        public String remainingPart() {
            return rightPart;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PositiveResult that = (PositiveResult) o;
            return Objects.equals(params, that.params)
                    && Objects.equals(rightPart, that.rightPart);
        }

        @Override
        public int hashCode() {
            return Objects.hash(params, rightPart);
        }
    }

}
