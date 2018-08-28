/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import static io.helidon.webserver.utils.TestUtils.toMap;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * The PathTemplateRegexTest.
 */
public class PathMatcherRegexTest {

    @Test
    public void testSimpleVar() throws Exception {
        String pathVarPattern = "(?<var1>[^/]+)";

        patternTest(true, "a/" + pathVarPattern + "/c", "a/b/c", toMap("var1", "b"));
        patternTest(false, "a/" + pathVarPattern + "/d", "a/b/c/d");

        patternTest(false, pathVarPattern + "/c", "a/b/c");
        patternTest(false, pathVarPattern, "a/b/c");
        patternTest(false, pathVarPattern, "a/b");
        patternTest(true, pathVarPattern, "a", toMap("var1", "a"));
    }

    private void patternTest(boolean matches, String patternString, String input) {
        patternTest(matches, patternString, input, Collections.emptyMap());
    }
    private void patternTest(boolean matches, String patternString, String input, Map<String, String> stringMap) {

        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(input);
        assertThat("", matcher.matches(), Is.is(matches));

        if (!matches) {
            return;
        }

        for (Map.Entry<String, String> entry : stringMap.entrySet()) {
            assertThat(matcher.group(entry.getKey()), Is.is(entry.getValue()));
        }
    }
}
