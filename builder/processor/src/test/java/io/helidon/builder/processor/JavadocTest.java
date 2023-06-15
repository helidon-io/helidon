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

package io.helidon.builder.processor;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class JavadocTest {
    @Test
    void testJavadoc() {
        String doc = """
                Compute very complex computation.
                Then compute another one.
                                
                And the last.
                @param algorithm defines computation algorithm
                  has more than one line
                  even a third one!
                @param style style of this computation
                @param complexity set to hard
                @return multiline
                    return
                    documentation
                @see see it!
                @customTag custom
                    tag
                    doc
                """;
        Javadoc j = Javadoc.parse(List.of(doc.split("\n")));
        assertThat(j.lines(), contains("Compute very complex computation.",
                                       "Then compute another one.",
                                       "",
                                       "And the last."));
        assertThat(j.returns(), contains("multiline", "return", "documentation"));
        assertThat(j.parameters(), contains(new Javadoc.Tag("algorithm", List.of("defines computation algorithm",
                                                                                 "has more than one line",
                                                                                 "even a third one!")),
                                            new Javadoc.Tag("style", List.of("style of this computation")),
                                            new Javadoc.Tag("complexity", List.of("set to hard"))));
        assertThat(j.nonParamTags(), contains(new Javadoc.Tag("see", List.of("see it!")),
                                              new Javadoc.Tag("customTag", List.of("custom",
                                                                                   "tag",
                                                                                   "doc"))));
    }
}
