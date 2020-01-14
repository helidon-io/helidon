/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.config.testing;

import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import static org.hamcrest.Matchers.any;

/**
 * Matchers for {@link java.util.Optional}.
 */
public final class OptionalMatcher {
    private OptionalMatcher() {
    }

    /**
     * A matcher for an {@link java.util.Optional} that checks it is
     * present, and that the value matches the provided matcher.
     * <p>
     * Usage example:
     * <pre>
     *     assertThat(myOptional, value(is("expected")));
     * </pre>
     * @param matcher matcher to validate the content of the optional
     * @param <T> type of the value
     * @return matcher validating the {@link java.util.Optional} is present and matches the provided matcher
     */
    public static <T> Matcher<Optional<T>> value(Matcher<T> matcher) {
        return new WithValue<>(matcher);
    }

    /**
     * A matcher for an {@link java.util.Optional} that checks it is empty.
     * <p>
     * Usage example:
     * <pre>
     *     assertThat(myOptional, empty());
     * </pre>
     * @param <T> type of the optional
     * @return matcher validating the {@link java.util.Optional} is empty
     */
    public static <T> Matcher<Optional<T>> empty() {
        return new Empty<>();
    }

    /**
     * A matcher for an {@link java.util.Optional} that checks it is
     * present.
     * <p>
     * Usage example:
     * <pre>
     *     assertThat(myOptional, present());
     * </pre>
     * @param <T> type of the value
     * @return matcher validating the {@link java.util.Optional} is present
     */
    public static <T> Matcher<Optional<T>> present() {
        return new WithValue<>(any(Object.class));
    }

    private static class Empty<T> extends TypeSafeMatcher<Optional<T>> {
        @Override
        protected boolean matchesSafely(Optional<T> item) {
            return item.isEmpty();
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("empty Optional");
        }

        @Override
        protected void describeMismatchSafely(Optional<T> item, Description mismatchDescription) {
            if (item.isPresent()) {
                mismatchDescription.appendText("not empty Optional, and value is ");
                mismatchDescription.appendValue(item.get());
            }
        }
    }

    private static class WithValue<T> extends TypeSafeMatcher<Optional<T>> {
        private final Matcher<? super T> matcher;

        WithValue(Matcher<? super T> matcher) {
            this.matcher = matcher;
        }

        @Override
        protected boolean matchesSafely(Optional<T> item) {
            return item.map(matcher::matches)
                    .orElse(false);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("not empty Optional, and value ");
            description.appendDescriptionOf(matcher);
        }

        @Override
        protected void describeMismatchSafely(Optional<T> item, Description mismatchDescription) {
            if (item.isPresent()) {
                mismatchDescription.appendText("not empty Optional, and value ");
                matcher.describeMismatch(item.get(), mismatchDescription);
            } else {
                mismatchDescription.appendText("is empty");
            }
        }
    }
}
