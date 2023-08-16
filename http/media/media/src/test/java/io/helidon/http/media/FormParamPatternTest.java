/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.http.media;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class FormParamPatternTest {
    @Test
    void testUrlEncoded() {
        String encodedString = "multiple=value1,value2&special=some+%26%40%23%2F+special+value&noValue=";
        //Pattern pattern = FormParamsSupport.FormParamsUrlReader.PATTERN;
        Pattern pattern = Pattern.compile("([^=]+)=([^&]*)&?");

        Matcher matcher = pattern.matcher(encodedString);

        List<Param> matched = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = matcher.group(2);
            matched.add(new Param(name, value));
        }

        assertThat(matched, hasItems(
                is(new Param("multiple", "value1,value2")),
                is(new Param("special", "some+%26%40%23%2F+special+value")),
                is(new Param("noValue", ""))));
    }

    private record Param(String name, String value) {
    }
}
