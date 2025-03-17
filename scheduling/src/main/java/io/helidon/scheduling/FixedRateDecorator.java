/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.scheduling;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.helidon.builder.api.Prototype;

final class FixedRateDecorator extends TaskConfigDecorator<FixedRateConfig.BuilderBase<?, ?>> {
    @SuppressWarnings("removal")
    @Override
    public void decorate(FixedRateConfig.BuilderBase<?, ?> target) {
        super.decorate(target);

        // new values are set using the option decorators below, now we can just re-set the deprecated values
        target.initialDelay(target.delayBy().toMillis());
        target.delay(target.interval().map(Duration::toMillis).orElse(1000L));
        target.timeUnit(TimeUnit.MILLISECONDS);
    }

    @SuppressWarnings("removal")
    static final class InitialDelayDecorator implements Prototype.OptionDecorator<FixedRateConfig.BuilderBase<?, ?>, Long> {
        @Override
        public void decorate(FixedRateConfig.BuilderBase<?, ?> builder, Long optionValue) {
            builder.delayBy(Duration.of(optionValue, builder.timeUnit().toChronoUnit()));
        }
    }

    static final class DelayDecorator implements Prototype.OptionDecorator<FixedRateConfig.BuilderBase<?, ?>, Long> {
        @SuppressWarnings("removal")
        @Override
        public void decorate(FixedRateConfig.BuilderBase<?, ?> builder, Long optionValue) {
            builder.interval(Duration.of(optionValue, builder.timeUnit().toChronoUnit()));
        }
    }

    @SuppressWarnings("removal")
    static final class TimeUnitDecorator implements Prototype.OptionDecorator<FixedRateConfig.BuilderBase<?, ?>, TimeUnit> {
        @Override
        public void decorate(FixedRateConfig.BuilderBase<?, ?> builder, TimeUnit optionValue) {
            builder.delay().ifPresent(it -> builder.interval(Duration.of(it, optionValue.toChronoUnit())));
            builder.initialDelay().ifPresent(it -> builder.delayBy(Duration.of(it, optionValue.toChronoUnit())));
        }
    }
}
