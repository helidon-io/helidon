/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.cors;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;

import java.util.Optional;

/**
 * Some useful custom matchers.
 */
class CustomMatchers {

    static <T> IsPresent<T> isPresent(Matcher matcher) {
        return new IsPresent(matcher);
    }

    static <T> IsPresent<T> isPresent() {
        return isPresent(null);
    }

    static IsNotPresent isNotPresent() {
        return new IsNotPresent();
    }

    /**
     * Makes sure the {@code Optional} is present, and if an additional matcher was provider, makes sure that the optional's
     * value passes the matcher.
     *
     * @param <T> type of the value in the Optional
     */
    static class IsPresent<T> extends TypeSafeMatcher<Optional<T>> {

        private final Matcher<T> matcher;

        IsPresent(Matcher<T> m) {
            matcher = m;
        }

        IsPresent() {
            matcher = null;
        }

        @Override
        protected boolean matchesSafely(Optional<T> t) {
            return t.isPresent() && (matcher == null || matcher.matches(t.get()));
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("is present");
            if (matcher != null) {
                description.appendText(" and matches " + matcher.toString());
            }
        }
    }

    static class IsNotPresent extends TypeSafeMatcher<Optional<? extends Object>> {

        @Override
        protected boolean matchesSafely(Optional<? extends Object> o) {
            return !o.isPresent();
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("is not present");
        }
    }
}
