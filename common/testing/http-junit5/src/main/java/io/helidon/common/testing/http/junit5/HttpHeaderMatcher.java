/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.common.testing.http.junit5;

import java.util.List;

import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;

/**
 * Matchers for {@link io.helidon.http.Headers}.
 */
public final class HttpHeaderMatcher {
    private HttpHeaderMatcher() {
    }

    /**
     * A matcher for an {@link io.helidon.http.Headers} that checks that the header is not present.
     * <p>
     * Usage example:
     * <pre>
     *     assertThat(httpHeaders, noHeader(HeaderNames.CONNECTION));
     * </pre>
     *
     * @param name header name
     * @return matcher validating the {@link io.helidon.http.Headers} does not contain the provided header
     */
    public static Matcher<Headers> noHeader(HeaderName name) {
        return new NoHeaderMatcher(name);
    }

    /**
     * A matcher for an {@link io.helidon.http.Headers} that checks that the header is present, ignoring its value.
     * <p>
     * Usage example:
     * <pre>
     *     assertThat(httpHeaders, hasHeader(HeaderNames.CONNECTION));
     * </pre>
     *
     * @param name header name
     * @return matcher validating the {@link io.helidon.http.Headers} does contain the provided header regardless of its
     *         value(s)
     */
    public static Matcher<Headers> hasHeader(HeaderName name) {
        return new HasHeaderMatcher(name);
    }

    /**
     * A matcher for an {@link io.helidon.http.Headers} that checks that the header is present and has the defined
     * value.
     * <p>
     * Usage example:
     * <pre>
     *     assertThat(httpHeaders, hasHeader(HeaderValues.CONNECTION_CLOSE));
     * </pre>
     *
     * @param header http header with values
     * @return matcher validating the {@link io.helidon.http.Headers} does contain the provided header
     */
    public static Matcher<Headers> hasHeader(Header header) {
        return new HasValueMatcher(header);
    }

    /**
     * A matcher for an {@link io.helidon.http.Headers} that checks that the header is present and has the defined
     * value(s).
     * <p>
     * Usage example:
     * <pre>
     *     assertThat(httpHeaders, hasHeader(HeaderValues.REDIRECT, "/location"));
     * </pre>
     *
     * @param name  http header name
     * @param value value(s) of the header
     * @return matcher validating the {@link io.helidon.http.Headers} does contain the provided header
     */
    public static Matcher<Headers> hasHeader(HeaderName name, String... value) {
        return new HasValueMatcher(HeaderValues.create(name, value));
    }

    /**
     * A matcher for an {@link io.helidon.http.Headers} that checks that the header is present and values
     * match the provided matcher.
     * <p>
     * Usage example:
     * <pre>
     *     assertThat(httpHeaders, hasHeader(HeaderNames.CONNECTION, contains("close")));
     * </pre>
     *
     * @param name          header name
     * @param valuesMatcher matcher to validate the values are OK
     * @return matcher validating the {@link io.helidon.http.Headers} does contain the expected values
     */
    public static Matcher<Headers> hasHeader(HeaderName name, Matcher<Iterable<? extends String>> valuesMatcher) {
        return new HasValueMatcher(name, valuesMatcher);
    }

    /**
     * A matcher for an {@link io.helidon.http.Headers} that checks that the header is present and its single value
     * matches the provided matcher.
     * <p>
     * Usage example:
     * <pre>
     *     assertThat(httpHeaders, hasHeaderValue(HeaderNames.CONNECTION, startsWith("c")));
     * </pre>
     *
     * @param name         header name
     * @param valueMatcher matcher to validate the value is OK
     * @return matcher validating the {@link io.helidon.http.Headers} does contain the expected value
     */
    public static Matcher<Headers> hasHeaderValue(HeaderName name, Matcher<String> valueMatcher) {
        return new HasSingleValueMatcher(name, valueMatcher);
    }

    private static class HasHeaderMatcher extends TypeSafeMatcher<Headers> {
        private final HeaderName name;

        HasHeaderMatcher(HeaderName header) {
            this.name = header;
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(name.defaultCase())
                    .appendText(" should be present");
        }

        @Override
        protected boolean matchesSafely(Headers httpHeaders) {
            return httpHeaders.contains(name);
        }

        @Override
        protected void describeMismatchSafely(Headers item, Description mismatchDescription) {
            mismatchDescription.appendValue(name.defaultCase()).appendText(" header is not present");
        }
    }

    private static class HasValueMatcher extends TypeSafeMatcher<Headers> {
        private final HeaderName name;
        private final Matcher<Iterable<? extends String>> valuesMatcher;

        HasValueMatcher(Header header) {
            this.name = header.headerName();
            this.valuesMatcher = Matchers.containsInAnyOrder(header.allValues().toArray(new String[0]));
        }

        HasValueMatcher(HeaderName name, Matcher<Iterable<? extends String>> valuesMatcher) {
            this.name = name;
            this.valuesMatcher = valuesMatcher;
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(name.defaultCase()).appendText(" should be present and values should match ");
            valuesMatcher.describeTo(description);
        }

        @Override
        protected boolean matchesSafely(Headers httpHeaders) {
            if (httpHeaders.contains(name)) {
                return valuesMatcher.matches(httpHeaders.all(name, List::of));
            }
            return false;
        }

        @Override
        protected void describeMismatchSafely(Headers item, Description mismatchDescription) {
            if (item.contains(name)) {
                List<String> all = item.all(name, List::of);
                mismatchDescription.appendValue(name.defaultCase()).appendText(" header is present with wrong values ");
                valuesMatcher.describeMismatch(all, mismatchDescription);
            } else {
                mismatchDescription.appendValue(name.defaultCase()).appendText(" header is not present");
            }
        }
    }

    private static class HasSingleValueMatcher extends TypeSafeMatcher<Headers> {
        private final HeaderName name;
        private final Matcher<String> valuesMatcher;

        HasSingleValueMatcher(HeaderName name, Matcher<String> valuesMatcher) {
            this.name = name;
            this.valuesMatcher = valuesMatcher;
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(name.defaultCase()).appendText(" should be present and value should match ");
            valuesMatcher.describeTo(description);
        }

        @Override
        protected boolean matchesSafely(Headers httpHeaders) {
            if (httpHeaders.contains(name)) {
                Header headerValue = httpHeaders.get(name);
                if (headerValue.allValues().size() == 1) {
                    return valuesMatcher.matches(headerValue.get());
                }
                return false;
            }
            return false;
        }

        @Override
        protected void describeMismatchSafely(Headers item, Description mismatchDescription) {
            if (item.contains(name)) {
                List<String> all = item.all(name, List::of);
                if (all.size() == 1) {
                    mismatchDescription.appendValue(name.defaultCase()).appendText(" header is present with wrong value ");
                    valuesMatcher.describeMismatch(all, mismatchDescription);
                } else {
                    mismatchDescription.appendValue(name.defaultCase()).appendText(" header is present with more than one value");
                }
            } else {
                mismatchDescription.appendValue(name.defaultCase()).appendText("header is not present");
            }
        }
    }

    private static class NoHeaderMatcher extends TypeSafeMatcher<Headers> {
        private final HeaderName name;

        private NoHeaderMatcher(HeaderName name) {
            this.name = name;
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(name.defaultCase()).appendText(" should not be present");
        }

        @Override
        protected boolean matchesSafely(Headers httpHeaders) {
            return !httpHeaders.contains(name);
        }

        @Override
        protected void describeMismatchSafely(Headers item, Description mismatchDescription) {
            if (item.contains(name)) {
                mismatchDescription.appendValue(name.defaultCase())
                        .appendText(" header is present, and its value is ");
                mismatchDescription.appendValue(item.get(name).allValues());
            }
        }
    }
}
