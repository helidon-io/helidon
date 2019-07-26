/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.openapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the OpenAPI parameter utility methods.
 */
public class ParameterUtilTest {

    public ParameterUtilTest() {
    }

    @Test
    public void testBadFormat() {
        assertThrows(IllegalArgumentException.class, () -> {
                ParameterUtil.parser("w$x$y", "dollar");
            });
    }

    @Test
    public void checkAllFormats() {
        check("ww,xx,yy", "csv", "ww", "xx", "yy");
        check("aaa bbb ccc", "ssv", "aaa", "bbb", "ccc");
        check("m\tn\to", "tsv", "m", "n", "o");
        check("g-g|h-h|i-i|j", "pipes", "g-g", "h-h", "i-i", "j");
        check("1,2,3", "multi", "1", "2", "3");
        check("z", "csv", "z");
    }

    private void check(String input, String format, String... expected) {
        final List<String> expectedList = new ArrayList<>();
        Collections.addAll(expectedList, expected);
        final List<String> parsed = ParameterUtil.parser(input, format);
        assertEquals(expectedList, parsed, "Unexpected results for format " + format);
    }
}
