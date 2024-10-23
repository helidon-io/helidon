/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import org.eclipse.microprofile.metrics.MetricID;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

class MetricIDMatcher {

    static WithName withName(Matcher<? super String> matcher) {
        return new WithName(matcher);
    }

    private static class WithName extends TypeSafeMatcher<MetricID> {

        private final Matcher<? super String> matcher;

        private WithName(Matcher<? super String> matcher) {
            this.matcher = matcher;
        }

        @Override
        protected boolean matchesSafely(MetricID item) {
            return matcher.matches(item.getName());
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("metric ID name");
            description.appendDescriptionOf(matcher);
        }
    }

}
