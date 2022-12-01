/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.openapi;

import io.helidon.common.http.MediaType;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

/**
 * Matcher for {@link io.helidon.common.http.MediaType}.
 */
public class MediaTypeMatcher {

    private MediaTypeMatcher() {
    }

    /**
     * Matcher for a {@link io.helidon.common.http.MediaType} checking if the {@code test} method returns true.
     *
     * <p>
     *     Example:
     *     <pre>
     *         assertThat("Returned media type", myMediaType, test(MediaType.TEXT_HTML));
     *     </pre>
     * </p>
     * <p>
     *     Combine with {@link io.helidon.config.testing.OptionalMatcher}:
     *     <pre>
     *         assertThat("Response media type",
     *                    response.headers().contentType(),
     *                    OptionalMatcher.value(test(MediaType.TEXT_HTML)));
     *     </pre>
     * </p>
     * @param expectedMediaType expected {@code MediaType}
     * @return matcher checking if the {@code test} method for the {@code MediaType} returns true
     */
    public static Matcher<MediaType> test(MediaType expectedMediaType) {
        return new MediaTypeTest(expectedMediaType);
    }

    private static class MediaTypeTest extends TypeSafeMatcher<MediaType> {
        private final MediaType expectedMediaType;

        MediaTypeTest(MediaType expectedMediaType) {
            this.expectedMediaType = expectedMediaType;
        }

        @Override
        protected boolean matchesSafely(MediaType item) {
            return item.test(expectedMediaType);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("a MediaType compatible with " + expectedMediaType.toString());
        }

        @Override
        protected void describeMismatchSafely(MediaType item, Description mismatchDescription) {
            mismatchDescription.appendText("was " + item.toString());
        }
    }
}
