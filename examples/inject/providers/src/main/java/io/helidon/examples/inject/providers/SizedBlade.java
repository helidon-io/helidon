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

package io.helidon.examples.inject.providers;

import java.util.Objects;

/**
 * See {@link Blade}
 */
class SizedBlade implements Blade {

    private final Size size;

    public enum Size {
        SMALL,
        LARGE
    }

    public SizedBlade(Size size) {
        this.size = Objects.requireNonNull(size);
    }

    @Override
    public String name() {
        return size + " Blade";
    }

    @Override
    public String toString() {
        return name();
    }

}
