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

package io.helidon.pico.testsubjects;

import java.util.Objects;

import io.helidon.pico.Bootstrap;

public class PicoServices1 extends AbstractPicoServices {
    private final Bootstrap bootstrap;

    public PicoServices1(Bootstrap bootstrap) {
        this.bootstrap = Objects.requireNonNull(bootstrap);
    }

    @Override
    public Bootstrap bootstrap() {
        return bootstrap;
    }

}
