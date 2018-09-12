/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.http;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link Utils}.
 */
class UtilsTest {

    @Test
    void tokenize() throws Exception {
        String text = ",aa,,fo\"oooo\",\"bar\",co\"o'l,e\"c,df'hk,lm',";
        List<String> tokens = Utils.tokenize(',', null, false, text);
        assertThat(tokens, contains("aa", "fo\"oooo\"", "\"bar\"", "co\"o'l", "e\"c", "df'hk", "lm'"));
        tokens = Utils.tokenize(',', null, true, text);
        assertThat(tokens, contains("", "aa", "", "fo\"oooo\"", "\"bar\"", "co\"o'l", "e\"c", "df'hk", "lm'", ""));
        tokens = Utils.tokenize(',', "\"", false, text);
        assertThat(tokens, contains("aa", "fo\"oooo\"", "\"bar\"", "co\"o'l,e\"c", "df'hk", "lm'"));
        tokens = Utils.tokenize(',', "'", false, text);
        assertThat(tokens, contains("aa", "fo\"oooo\"", "\"bar\"", "co\"o'l,e\"c,df'hk", "lm',"));
        tokens = Utils.tokenize(',', "\"'", false, text);
        assertThat(tokens, contains("aa", "fo\"oooo\"", "\"bar\"", "co\"o'l,e\"c", "df'hk,lm'"));
        tokens = Utils.tokenize(',', "\"'", true, text);
        assertThat(tokens, contains("", "aa", "", "fo\"oooo\"", "\"bar\"", "co\"o'l,e\"c", "df'hk,lm'", ""));
        tokens = Utils.tokenize(';', "\"'", true, text);
        assertEquals(1, tokens.size());
        assertEquals(text, tokens.get(0));
    }

    @Test
    void testByteBufferToByteArray() {
        byte[] array = "halleluja".getBytes(StandardCharsets.UTF_8);
        ByteBuffer wrap = ByteBuffer.wrap(array);
        byte[] unwrapped = Utils.toByteArray(wrap);

        assertThat(unwrapped, is(array));
    }
}
