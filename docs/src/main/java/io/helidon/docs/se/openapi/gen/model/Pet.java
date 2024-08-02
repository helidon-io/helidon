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
package io.helidon.docs.se.openapi.gen.model;

import java.util.List;

public class Pet {

    public String getName() {
        return null;
    }

    public long getId() {
        return 0L;
    }

    public enum StatusEnum {
        AVAILABLE;

        public String value() {
            return "";
        }
    }

    public List<Tag> getTags() {
        return List.of();
    }
}
