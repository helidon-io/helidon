/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.tests.integration.nativeimage.se1;

import io.helidon.common.Reflected;

@Reflected
public class Animal {
    private TYPE type;
    private String name;

    public Animal() {
    }

    public Animal(final TYPE type, final String name) {
        this.type = type;
        this.name = name;
    }

    public TYPE getType() {
        return type;
    }

    public void setType(final TYPE type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public enum TYPE {
        BIRD, DOG, CAT
    }
}
