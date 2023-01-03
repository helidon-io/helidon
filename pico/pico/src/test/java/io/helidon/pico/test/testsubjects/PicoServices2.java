/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.test.testsubjects;

import java.util.Objects;

import io.helidon.pico.Bootstrap;
import io.helidon.pico.PicoServices;
import io.helidon.pico.Services;

import jakarta.inject.Singleton;

@Singleton
public class PicoServices2 implements PicoServices {
    private final Bootstrap bootstrap;

    public PicoServices2(Bootstrap bootstrap) {
        this.bootstrap = Objects.requireNonNull(bootstrap);
    }

    @Override
    public Services services() {
        return null;
    }

    @Override
    public Bootstrap bootstrap() {
        return bootstrap;
    }

}
