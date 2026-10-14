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

package io.helidon.builder.test;

import io.helidon.builder.test.testsubjects.OptionDecorators;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class OptionDecoratorTest {
    @Test
    @SuppressWarnings("deprecation")
    void deprecatedPrimitiveGetterRetainsDefault() {
        var builder = OptionDecorators.builder();

        assertThat(builder.legacyValue(), is(42));
        assertThat(builder.replacementValue(), is(42));
    }

    @Test
    void copyDoesNotApplyUnsetDeprecatedAlias() {
        var source = OptionDecorators.builder().replacementValue(64);

        var copy = OptionDecorators.builder().from(source);

        assertThat(copy.replacementValue(), is(64));
    }

    @Test
    @SuppressWarnings("deprecation")
    void copyAppliesExplicitDeprecatedAlias() {
        var source = OptionDecorators.builder().legacyValue(0);

        var copy = OptionDecorators.builder().from(source);

        assertThat(copy.replacementValue(), is(0));
        assertThat(copy.legacyValue(), is(0));
    }

    @Test
    @SuppressWarnings("deprecation")
    void copyPreservesDeprecatedGetterOverride() {
        var copy = OptionDecorators.builder().from(new LegacyBuilder());

        assertThat(copy.legacyValue(), is(0));
        assertThat(copy.replacementValue(), is(0));
    }

    @Test
    @SuppressWarnings("deprecation")
    void copyPreservesFloatingPointGetterOverride() {
        var copy = OptionDecorators.builder().from(new LegacyBuilder());

        assertThat(copy.legacyDouble(), is(-0.0));
        assertThat(copy.replacementDouble(), is(-0.0));
        assertThat(copy.legacyFloat(), is(-0.0F));
        assertThat(copy.replacementFloat(), is(-0.0F));
    }

    @Test
    @SuppressWarnings("deprecation")
    void copyDoesNotReapplyUnchangedNaNAlias() {
        var source = OptionDecorators.builder()
                .legacyDouble(Double.NaN)
                .replacementDouble(64.0)
                .legacyFloat(Float.NaN)
                .replacementFloat(64.0F);

        var copy = OptionDecorators.builder().from(source);

        assertThat(copy.legacyDouble(), is(Double.NaN));
        assertThat(copy.replacementDouble(), is(64.0));
        assertThat(copy.legacyFloat(), is(Float.NaN));
        assertThat(copy.replacementFloat(), is(64.0F));
    }

    @Test
    void deprecatedPrimitiveGetterDescriptorIsPreserved() throws NoSuchMethodException {
        var method = OptionDecorators.BuilderBase.class.getMethod("legacyValue");

        assertThat(method.getReturnType(), sameInstance(int.class));
    }

    private static final class LegacyBuilder extends OptionDecorators.BuilderBase<LegacyBuilder, OptionDecorators> {
        @Override
        public int legacyValue() {
            return 0;
        }

        @Override
        public double legacyDouble() {
            return -0.0;
        }

        @Override
        public float legacyFloat() {
            return -0.0F;
        }

        @Override
        public OptionDecorators buildPrototype() {
            return OptionDecorators.builder().from(this).buildPrototype();
        }
    }
}
