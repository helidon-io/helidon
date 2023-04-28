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

package io.helidon.builder.testing.utils;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.helidon.builder.testing.utils.DefaultDiff.*;
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
        Cart cart = DefaultCart.builder()
                .build();
        Map<String, String> map = BuilderUtils.expand(cart);
        assertThat(map.toString(), map.size(),
                   is(0));

        cart = DefaultCart.toBuilder(cart)
                .addFruit(DefaultApple.builder().color("red").peel(DefaultPeel.builder().edible(true).build()).build())
                .addFruit(DefaultApple.builder().color("green").peel(DefaultPeel.builder().edible(true).build()).build())
                .addFruit(DefaultOrange.builder().peel(DefaultPeel.builder().edible(false).build()))
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
        Cart cart1 = DefaultCart.builder()
                .addFruit(DefaultApple.builder().color("red").peel(DefaultPeel.builder().edible(true).build()).build())
                .addFruit(DefaultOrange.builder().peel(DefaultPeel.builder().edible(false).build()))
                .build();
        Cart cart2 = DefaultCart.builder()
                .addFruit(DefaultApple.builder().color("green").peel(DefaultPeel.builder().edible(true).build()).build())
                .addFruit(DefaultOrange.builder().peel(DefaultPeel.builder().edible(false).build()))
                .addFruit(DefaultApple.builder().color("yellow").peel(DefaultPeel.builder().edible(true).build()).build())
                .build();
        List<Diff> diffs = BuilderUtils.diff(cart1, cart2);
        assertThat(diffs, contains(
                builder().key("fruits[0].color").leftSide("red").rightSide("green").build(),
                builder().key("fruits[2]").rightSide("io.helidon.builder.testing.utils.DefaultApple").build(),
                builder().key("fruits[2].color").rightSide("yellow").build(),
                builder().key("fruits[2].peel").rightSide("io.helidon.builder.testing.utils.DefaultPeel").build(),
                builder().key("fruits[2].peel.edible").rightSide("true").build()
               ));
    }
}
