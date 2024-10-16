/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject;

import java.util.List;
import java.util.stream.Collectors;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.registry.Service;

final class ParameterizedTypes {
    private ParameterizedTypes() {
    }

    @Service.Contract
    interface Color {
        String name();
    }

    @Service.Contract
    interface Circle<T extends Color> {
        T color();
    }

    @Injection.Singleton
    static class Blue implements Color {
        @Override
        public String name() {
            return "blue";
        }
    }

    @Injection.Singleton
    static class Green implements Color {
        @Override
        public String name() {
            return "green";
        }
    }

    @Injection.Singleton
    @Weight(Weighted.DEFAULT_WEIGHT + 10)
    record BlueCircle(Blue color) implements Circle<Blue> {
    }

    @Injection.Singleton
    record GreenCircle(Green color) implements Circle<Green> {
    }

    @Injection.Singleton
    static class ColorReceiver {
        private final List<Circle<Color>> circles;

        @Injection.Inject
        ColorReceiver(List<Circle<Color>> circles) {
            this.circles = circles;
        }

        String getString() {
            return circles.stream()
                    .map(Circle::color)
                    .map(Color::name)
                    .collect(Collectors.joining("-"));
        }
    }

    @Injection.Singleton
    static class ColorsReceiver {
        private final Circle<Green> greenCircle;
        private final Circle<Blue> blueCircle;

        @Injection.Inject
        ColorsReceiver(Circle<Green> greenCircle,
                       Circle<Blue> blueCircle) {
            this.greenCircle = greenCircle;
            this.blueCircle = blueCircle;
        }

        String getString() {
            return greenCircle.color().name() + "-" + blueCircle.color().name();
        }
    }
}
