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
package io.helidon.codegen.testing;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.intellij.lang.annotations.Language;

/**
 * Hamcrest matchers for codegen tests.
 * <p>
 * <b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or deletion without notice.</b>
 * </p>
 */
public final class CodegenMatchers {

    private CodegenMatchers() {
    }

    /**
     * Regex match that treats {@code //...} as multiline wildcards.
     *
     * @param expected expected string
     * @return matcher
     */
    public static Matcher<String> matches(@Language("java") String expected) {
        return new RegexMatcher(expected);
    }

    private static final class RegexMatcher extends TypeSafeMatcher<String> {

        private final String regex;

        RegexMatcher(String expected) {
            var regex = expected.lines().map(it -> {
                if (it.trim().startsWith("//...")) {
                    return ".*";
                } else {
                    return "\\Q" + it + "\\E";
                }
            }).collect(Collectors.joining("\n"));
            this.regex = expected.endsWith("\n") ? regex + "\n" : regex;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(regex);
        }

        @Override
        protected boolean matchesSafely(String item) {
            var pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.DOTALL);
            return pattern.matcher(item).matches();
        }
    }
}
