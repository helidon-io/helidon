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

package io.helidon.builder.testing;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.helidon.builder.testing.DiffDefault.builder;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;

class BuilderUtilsTest {

    @Test
    void expand() {
        Cart cart = CartDefault.builder()
                .build();
        Map<String, String> map = BuilderUtils.expand(cart);
        assertThat(map.toString(), map.size(),
                   is(0));

        cart = CartDefault.toBuilder(cart)
                .addFruit(AppleDefault.builder().color("red").peel(PeelDefault.builder().edible(true).build()).build())
                .addFruit(AppleDefault.builder().color("green").peel(PeelDefault.builder().edible(true).build()).build())
                .addFruit(OrangeDefault.builder().peel(PeelDefault.builder().edible(false).build()))
                .build();
        map = BuilderUtils.expand(cart);
        assertThat(map.toString(), map,
                   hasEntry("fruits[0].color", "red"));
        assertThat(map.toString(), map,
                   hasEntry("fruits[0].peel.edible", "true"));
        assertThat(map.toString(), map,
                   hasEntry("fruits[1].color", "green"));
        assertThat(map.toString(), map,
                   hasEntry("fruits[1].peel.edible", "true"));
        assertThat(map.toString(), map,
                   not(hasKey("fruits[2].color")));
        assertThat(map.toString(), map,
                   hasEntry("fruits[2].peel.edible", "false"));

        RuntimeException e = Assertions.assertThrows(RuntimeException.class, () -> BuilderUtils.expand(this));
        assertThat(e.getMessage(),
                   equalTo("Expected to find a usable visitAttributes method"));
    }

    @Test
    void diff() {
        Cart cart1 = CartDefault.builder()
                .addFruit(AppleDefault.builder().color("red").peel(PeelDefault.builder().edible(true).build()).build())
                .addFruit(OrangeDefault.builder().peel(PeelDefault.builder().edible(false).build()))
                .build();
        Cart cart2 = CartDefault.builder()
                .addFruit(AppleDefault.builder().color("green").peel(PeelDefault.builder().edible(true).build()).build())
                .addFruit(OrangeDefault.builder().peel(PeelDefault.builder().edible(false).build()))
                .addFruit(AppleDefault.builder().color("yellow").peel(PeelDefault.builder().edible(true).build()).build())
                .build();
        List<Diff> diffs = BuilderUtils.diff(cart1, cart2);
        assertThat(diffs, contains(
                builder().key("fruits[0].color").leftSide("red").rightSide("green").build(),
                builder().key("fruits[2]").rightSide(AppleDefault.class.getName()).build(),
                builder().key("fruits[2].color").rightSide("yellow").build(),
                builder().key("fruits[2].peel").rightSide(PeelDefault.class.getName()).build(),
                builder().key("fruits[2].peel.edible").rightSide("true").build()
               ));
    }
}
