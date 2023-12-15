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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriEncoding;
import io.helidon.common.uri.UriPath;

/**
 * Utility methods to create path matchers.
 */
public final class PathMatchers {
    private static final char[] REGEXP_META_CHARACTERS = "<([{\\^-=$!|]})?*+.>".toCharArray();
    private static final UriPath ROOT = UriPath.create("/");
    private static final RoutedPath ROUTED_ROOT = new NoParamRoutedPath(ROOT);

    private static final String PARAM_PREFIX = "gfXdbHQlk";

    static {
        // The REGEXP_META_CHARACTERS are used in binary search. IT MUST BE SORTED!
        Arrays.sort(REGEXP_META_CHARACTERS);
    }

    private PathMatchers() {
    }

    /**
     * Exact match path matcher.
     *
     * @param pathToMatch the path must match exactly the provided path
     * @return exact match path matcher
     */
    public static PathMatcher exact(String pathToMatch) {
        return new ExactPathMatcher(fixPrefix(pathToMatch));
    }

    /**
     * Prefix match path matcher.
     *
     * @param pathToMatch the path must be prefixed by the provided path
     * @return prefix match path matcher
     */
    public static PathMatcher prefix(String pathToMatch) {
        return new PrefixPathMatcher(fixPrefix(pathToMatch));
    }

    /**
     * Pattern match path matcher.
     *
     * @param pattern pattern that may contain parameters and allowed patterns
     * @return pattern match path matcher
     */
    public static PathMatcher pattern(String pattern) {
        StringBuilder regexp = new StringBuilder(pattern.length() * 2);
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
                        throw new IllegalStateException("Optional sequences cannot be nested! "
                                                                + "Pattern: " + pattern
                                                                + ", index: " + (iter.index() - 1));
                    } else {
                        optionalSequence = true;
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
                    String name = parseParameter(iter, regexp, paramCounter);
                    if (name.length() > 0) {
                        paramToGroupName.put(name, PARAM_PREFIX + paramCounter);
                        paramCounter++;
                    }
                    break;
                case '*':
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
        }
        // Build result
        if (optionalSequence) {
            throw new IllegalStateException("Missing end of a optional sequence (']' character)! "
                                                    + "Pattern: " + pattern
                                                    + ", index: " + (iter.index() - 1));
        }

        return new PatternPathMatcher(regexp.toString(), paramToGroupName);
    }

    /**
     * Create a path matcher from a path pattern.
     * This method will analyze the pattern and return appropriate path matcher.
     *
     * @param pathPattern path pattern to match agains
     * @return path matcher
     */
    public static PathMatcher create(String pathPattern) {
        // the following characters mark this as:
        // ends with /* and no other - prefix match
        // {...} - pattern with a named parameter
        // * - pattern glob
        // \ - special character (regexp)

        boolean prefix = false;
        String checkPattern = pathPattern;
        if (pathPattern.endsWith("/*")) {
            prefix = true;
            checkPattern = pathPattern.substring(0, pathPattern.length() - 2);
        }

        if (checkPattern.contains("{")
            || checkPattern.contains("[")
            || checkPattern.contains("*")
            || checkPattern.contains("\\")) {
            return pattern(pathPattern);
        }

        if (prefix) {
            return prefix(checkPattern + "/");
        }
        return exact(pathPattern);
    }

    /**
     * Path matcher matching any path.
     *
     * @return matcher matching any path
     */
    public static PathMatcher any() {
        return new AnyMatcher();
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
            case ':' -> {
                if (greedy) {
                    throw new IllegalStateException("Parameter modifier '+' cannot be combined with custom regexp!"
                                                            + " Text: " + iter.seq.toString()
                                                            + ", index: " + (iter.index() - 1));
                }
                String r1 = name.toString().trim();
                addParamRegexp(builder, r1.length() > 0 ? index : -1, parseParamRegexp(iter));
                return r1;
            }
            case '}' -> {
                String r2 = name.toString().trim();
                addParamRegexp(builder,
                               r2.length() > 0 ? index : -1,
                               greedy ? ".+" : "[^/]+");
                return r2;
            }
            default -> name.append(ch);
            }
        }
        throw new IllegalStateException("Pattern parameter has no end character '}'!"
                                                + " Text: " + iter.seq.toString()
                                                + ", index: " + (iter.index() - 1));
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
        throw new IllegalStateException("Pattern parameter has specified regexp but no end character '}'!"
                                                + " Text: " + iter.seq.toString()
                                                + ", index: " + (iter.index() - 1));
    }

    private static void addParamRegexp(StringBuilder builder, int nameIndex, String regexp) {
        builder.append("(");
        if (nameIndex >= 0) {
            builder.append("?<").append(PARAM_PREFIX).append(nameIndex).append('>');
        }
        builder.append(regexp);
        builder.append(')');
    }

    private static String fixPrefix(String pathToMatch) {
        if (pathToMatch.isEmpty()) {
            return "/";
        }
        if (pathToMatch.charAt(0) == '/') {
            return pathToMatch;
        }
        return "/" + pathToMatch;
    }

    static final class ExactPathMatcher implements PathMatcher {
        private final String path;
        private final String pathWithTrailingSlash;

        ExactPathMatcher(String path) {
            // We work with decoded URIs
            this.path = UriEncoding.decodeUri(path);
            this.pathWithTrailingSlash = this.path + "/";
        }

        @Override
        public MatchResult match(UriPath uriPath) {
            if (this.path.equals(uriPath.rawPath())) {
                // optimize - if raw is correct, do not go further (so we do not decode path if not needed)
                return new MatchResult(true, new NoParamRoutedPath(uriPath));
            }

            String decodedPath = uriPath.path();
            if (this.path.equals(decodedPath)) {
                return new MatchResult(true, new NoParamRoutedPath(uriPath));
            }
            return MatchResult.notAccepted();
        }

        @Override
        public PrefixMatchResult prefixMatch(UriPath uriPath) {
            if (path.equals("/")) {
                return new PrefixMatchResult(true,
                                             ROUTED_ROOT,
                                             uriPath);
            }

            String actualPath = uriPath.path();
            if (actualPath.startsWith(pathWithTrailingSlash) || actualPath.equals(path)) {
                // we have /test
                String remaining = actualPath.substring(path.length());
                if (remaining.isEmpty()) {
                    // we received /test
                    return new PrefixMatchResult(true,
                                                 new NoParamRoutedPath(uriPath),
                                                 UriPath.createRelative(uriPath, "/"));
                } else {
                    // we received /test/whatever, remaining should be /whatever
                    return new PrefixMatchResult(true,
                                                 new NoParamRoutedPath(UriPath.createRelative(uriPath, path)),
                                                 UriPath.createRelative(uriPath, remaining));
                }
            }
            return PrefixMatchResult.notAccepted();
        }

        @Override
        public String toString() {
            return "exact: " + path;
        }
    }

    static final class PrefixPathMatcher implements PathMatcher {
        private final String prefix;
        private final String exactMatch;

        PrefixPathMatcher(String prefix) {
            this.prefix = prefix;
            if (prefix.endsWith("/")) {
                exactMatch = prefix.substring(0, prefix.length() - 1);
            } else {
                exactMatch = prefix;
            }
        }

        @Override
        public MatchResult match(UriPath uriPath) {
            String decodedPath = uriPath.path();
            if (decodedPath.startsWith(prefix)) {
                // start with the prefix
                return new MatchResult(true, new NoParamRoutedPath(uriPath));
            }
            if (exactMatch.equals(decodedPath)) {
                // exact match (no trailing /)
                return new MatchResult(true, new NoParamRoutedPath(uriPath));
            }
            return MatchResult.notAccepted();
        }

        @Override
        public PrefixMatchResult prefixMatch(UriPath uriPath) {
            if (prefix.equals("/")) {
                return new PrefixMatchResult(true,
                                             ROUTED_ROOT,
                                             uriPath);
            }
            String actualPath = uriPath.path();
            if (actualPath.startsWith(prefix)) {
                // we have /test
                String remaining = actualPath.substring(prefix.length());
                if (remaining.isEmpty()) {
                    // we received /test
                    return new PrefixMatchResult(true,
                                                 new NoParamRoutedPath(uriPath),
                                                 UriPath.createRelative(uriPath, "/"));
                } else {
                    int slash = remaining.indexOf('/');
                    if (slash == -1) {
                        // prefix just matches it all
                        return new PrefixMatchResult(true,
                                                     new NoParamRoutedPath(uriPath),
                                                     UriPath.createRelative(uriPath, "/"));
                    } else {
                        // prefix matches the first segment
                        String matchedPath = prefix + remaining.substring(0, slash);
                        remaining = remaining.substring(slash);
                        // we received /test/whatever, remaining should be /whatever
                        return new PrefixMatchResult(true, new NoParamRoutedPath(UriPath.createRelative(uriPath,
                                                                                                        matchedPath)),
                                                     UriPath.createRelative(uriPath, remaining));
                    }
                }
            }
            return PrefixMatchResult.notAccepted();
        }

        @Override
        public String toString() {
            return "prefix: " + prefix;
        }
    }

    static class PatternPathMatcher implements PathMatcher {
        private static final String RIGHT_PART_PARAM_NAME = PARAM_PREFIX + "rightpart";

        private final Map<String, String> paramToGroupName;
        private final Pattern pattern;
        private final Pattern leftPattern;
        private final String patternString;

        PatternPathMatcher(String pattern, Map<String, String> paramToGroupName) {
            this.patternString = pattern;
            this.pattern = Pattern.compile(pattern);
            this.leftPattern = Pattern.compile(pattern + "(?<" + RIGHT_PART_PARAM_NAME + ">/.+)?");
            this.paramToGroupName = paramToGroupName;
        }

        @Override
        public MatchResult match(UriPath uriPath) {
            String decodedPath = uriPath.path();
            Matcher matcher = pattern.matcher(decodedPath);
            if (matcher.matches()) {
                return new MatchResult(true, new ParamRoutedPath(uriPath, extractParams(matcher)));
            }
            return MatchResult.notAccepted();
        }

        @Override
        public PrefixMatchResult prefixMatch(UriPath uriPath) {
            String decodedPath = uriPath.path();
            Matcher matcher = leftPattern.matcher(decodedPath);
            if (matcher.matches()) {
                String unmatched = matcher.group(RIGHT_PART_PARAM_NAME);
                if (unmatched == null || unmatched.isEmpty()) {
                    // all matched
                    unmatched = "/";
                }
                if (unmatched.startsWith("/")) {
                    String matched = decodedPath.substring(0, (decodedPath.length() - unmatched.length()));
                    // full segment matched
                    return new PrefixMatchResult(true,
                                                 new ParamRoutedPath(UriPath.createRelative(uriPath, matched),
                                                                     extractParams(matcher)),
                                                 UriPath.createRelative(uriPath, unmatched));
                }
            }

            return PrefixMatchResult.notAccepted();
        }

        @Override
        public String toString() {
            return "pattern: " + patternString;
        }

        private Parameters extractParams(Matcher matcher) {
            // Old school, but slightly faster than stream.
            Map<String, String> params = new HashMap<>(paramToGroupName.size());
            for (Map.Entry<String, String> entry : paramToGroupName.entrySet()) {
                String paramValue = matcher.group(entry.getValue());
                if (paramValue != null) {
                    params.put(entry.getKey(), paramValue);
                }
            }
            return Parameters.createSingleValueMap("http/path", params);
        }
    }

    static class AnyMatcher implements PathMatcher {
        @Override
        public MatchResult match(UriPath uriPath) {
            return new MatchResult(true, new NoParamRoutedPath(uriPath));
        }

        @Override
        public PrefixMatchResult prefixMatch(UriPath uriPath) {
            return new PrefixMatchResult(true,
                                         new NoParamRoutedPath(UriPath.create("")),
                                         uriPath);
        }

        @Override
        public String toString() {
            return "any path";
        }
    }

    // path with parameters
    private static class ParamRoutedPath extends NoParamRoutedPath {
        private final UriPath path;
        private final Parameters pathTemplateParams;

        private ParamRoutedPath(UriPath path, Parameters pathTemplateParams) {
            super(path);
            this.path = path;
            this.pathTemplateParams = pathTemplateParams;
        }

        @Override
        public Parameters pathParameters() {
            return pathTemplateParams;
        }

        @Override
        public RoutedPath absolute() {
            return new ParamRoutedPath(path.absolute(), pathTemplateParams);
        }

        @Override
        public String toString() {
            return path + " (" + pathTemplateParams + ")";
        }
    }

    // path without parameters
    private static class NoParamRoutedPath implements RoutedPath, Supplier<RoutedPath> {
        private static final Parameters EMPTY_PARAMS = Parameters.empty("http/path");
        private final UriPath path;

        NoParamRoutedPath(UriPath path) {
            this.path = path;
        }

        @Override
        public String rawPath() {
            return path.rawPath();
        }

        @Override
        public String rawPathNoParams() {
            return path.rawPathNoParams();
        }

        @Override
        public String path() {
            return path.path();
        }

        @Override
        public Parameters matrixParameters() {
            return path.matrixParameters();
        }

        @Override
        public void validate() {
            path.validate();
        }

        @Override
        public Parameters pathParameters() {
            return EMPTY_PARAMS;
        }

        @Override
        public RoutedPath absolute() {
            return new NoParamRoutedPath(path.absolute());
        }

        @Override
        public RoutedPath get() {
            return this;
        }

        @Override
        public String toString() {
            return path.toString();
        }
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
     * Path matching result.
     *
     * @param accepted      whether the matcher accepts this path
     * @param matchedPath   the routed path that was matched from the request with resolved parameters
     * @param unmatchedPath the HTTP path that is remaining to be matched by sub-resources
     */
    public record PrefixMatchResult(boolean accepted,
                                    RoutedPath matchedPath,
                                    UriPath unmatchedPath) {
        private static final PrefixMatchResult NOT_ACCEPTED = new PrefixMatchResult(false, null, null);

        /**
         * Not accepted path prefix matcher result.
         *
         * @return result that is not accepted
         */
        public static PrefixMatchResult notAccepted() {
            return NOT_ACCEPTED;
        }
    }

    /**
     * Path matching result.
     *
     * @param accepted whether the matcher accepts this path
     * @param path     the path to use to get parameter values (only if accepted by this route), {@code null} otherwise;
     *                 this value is ignored if not accepted
     */
    public record MatchResult(boolean accepted,
                              RoutedPath path) {
        private static final MatchResult NOT_ACCEPTED = new MatchResult(false, null);

        /**
         * Not accepted path matcher result.
         *
         * @return result that is not accepted
         */
        public static MatchResult notAccepted() {
            return NOT_ACCEPTED;
        }
    }
}
