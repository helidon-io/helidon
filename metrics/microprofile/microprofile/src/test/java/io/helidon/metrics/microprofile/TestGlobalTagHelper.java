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
package io.helidon.metrics.microprofile;

import java.util.Optional;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.is;


class TestGlobalTagHelper {

    @Test
    void checkSingle() {
        GlobalTagsHelper helper = new GlobalTagsHelper();
        Optional<Tag[]> tagsOpt = helper.tags("a=4");
        assertThat("Optional tags", tagsOpt, OptionalMatcher.optionalPresent());
        Tag[] tags = tagsOpt.get();
        assertThat("Single value assignments", tags, arrayWithSize(1));
        assertThat("Single assignment key", tags[0].getKey(), is("a"));
        assertThat("Single assignment value", tags[0].getValue(), is("4"));
    }

    @Test
    void checkMultiple() {
        GlobalTagsHelper helper = new GlobalTagsHelper();
        Optional<Tag[]> tagsOpt = helper.tags("a=11,b=12,c=13");
        assertThat("Optional tags", tagsOpt, OptionalMatcher.optionalPresent());
        Tag[] tags = tagsOpt.get();

        assertThat("Multiple value assignments", tags, arrayWithSize(3));
        assertThat("Multiple assignment key 0", tags[0].getKey(), is("a"));
        assertThat("Multiple assignment value 0", tags[0].getValue(), is("11"));
        assertThat("Multiple assignment key 1", tags[1].getKey(), is("b"));
        assertThat("Multiple assignment value 1", tags[1].getValue(), is("12"));
        assertThat("Multiple assignment key 2", tags[2].getKey(), is("c"));
        assertThat("Multiple assignment value 2", tags[2].getValue(), is("13"));
    }

    @Test
    void checkQuoted() {
        GlobalTagsHelper helper = new GlobalTagsHelper();
        Optional<Tag[]> tagsOpt = helper.tags("d=r\\=3,e=4,f=0\\,1,g=hi");
        assertThat("Optional tags", tagsOpt, OptionalMatcher.optionalPresent());
        Tag[] tags = tagsOpt.get();
        assertThat("Quoted value assignments", tags, arrayWithSize(4));
        assertThat("Quoted assignment key 0", tags[0].getKey(), is("d"));
        assertThat("Quoted assignment value 0", tags[0].getValue(), is("r=3"));
        assertThat("Quoted assignment key 1", tags[1].getKey(), is("e"));
        assertThat("Quoted assignment value 1", tags[1].getValue(), is("4"));
        assertThat("Quoted assignment key 2", tags[2].getKey(), is("f"));
        assertThat("Quoted assignment value 2", tags[2].getValue(), is("0,1"));
        assertThat("Quoted assignment key 3", tags[3].getKey(), is("g"));
        assertThat("Quoted assignment value 3", tags[3].getValue(), is("hi"));
    }

    @Test
    void checkErrors() {
        GlobalTagsHelper helper = new GlobalTagsHelper();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> helper.tags(""));
        assertThat("Exception for empty assignments", ex.getMessage(), containsString("empty"));

        ex = assertThrows(IllegalArgumentException.class, () -> helper.tags("a="));
        assertThat("Exception for empty assignments", ex.getMessage(), containsString("found 1"));

        ex = assertThrows(IllegalArgumentException.class, () -> helper.tags("=1"));
        assertThat("Exception for empty assignments", ex.getMessage(), containsString("left"));

        ex = assertThrows(IllegalArgumentException.class, () -> helper.tags("a*=1,"));
        assertThat("Exception for empty assignments", ex.getMessage(), containsString("tag name"));
    }
}
