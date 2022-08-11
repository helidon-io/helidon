/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.config.testing;

import io.helidon.config.spi.ConfigNode;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 * Hamcrest {@link org.hamcrest.Matcher} implementation that matches {@link io.helidon.config.spi.ConfigNode.ValueNode} value.
 */
public final class ValueNodeMatcher extends TypeSafeMatcher<ConfigNode> {

    private final String expectedValue;

    private ValueNodeMatcher(String expectedValue) {
        this.expectedValue = expectedValue;
    }

    /**
     * Creates new instance of {@link io.helidon.config.testing.ValueNodeMatcher} that matches
     * {@link io.helidon.config.spi.ConfigNode.ValueNode}
     * with spacified {@code expectedValue}.
     *
     * @param expectedValue expected value holded by {@link io.helidon.config.spi.ConfigNode.ValueNode}
     * @return new instance of {@link io.helidon.config.testing.ValueNodeMatcher}
     */
    public static ValueNodeMatcher valueNode(String expectedValue) {
        return new ValueNodeMatcher(expectedValue);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("ValueNode with value ").appendValue(this.expectedValue);
    }

    @Override
    public boolean matchesSafely(ConfigNode actualValue) {
        if (actualValue instanceof ConfigNode.ValueNode valueNode) {
            return expectedValue.equals(valueNode.get());
        }
        return false;
    }

    @Override
    public void describeMismatchSafely(ConfigNode item, Description description) {
        if (item instanceof ConfigNode.ValueNode valueNode) {
            description.appendText("got ")
                    .appendValue(valueNode.get());
        } else {
            description.appendText("got ")
                    .appendValue(item.getClass().getName())
                    .appendText(" ")
                    .appendValue(item);
        }
    }

}
