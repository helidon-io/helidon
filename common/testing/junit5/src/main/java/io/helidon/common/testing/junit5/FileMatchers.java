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
package io.helidon.common.testing.junit5;

import java.nio.file.Files;
import java.nio.file.Path;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

/**
 * Hamcrest matchers for {@link java.nio.file.Path}.
 */
public final class FileMatchers {
    private FileMatchers() {
    }

    /**
     * A matcher that tests if a {@link java.nio.file.Path} exists.
     *
     * @return matcher validating the {@link java.nio.file.Path} exists
     */
    public static Matcher<Path> fileExists() {
        return new FileExistMatcher();
    }

    private static final class FileExistMatcher extends TypeSafeMatcher<Path> {

        private FileExistMatcher() {
        }

        @Override
        protected boolean matchesSafely(Path actual) {
            return Files.exists(actual);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("File exists");
        }

        @Override
        protected void describeMismatchSafely(Path path, Description mismatchDescription) {
            mismatchDescription.appendText("File does not exist: " + path);
        }
    }
}
