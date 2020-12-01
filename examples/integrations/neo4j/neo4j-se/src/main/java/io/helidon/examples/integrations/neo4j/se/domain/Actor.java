/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.neo4j.se.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * author Michael J. Simons
 */
public class Actor {

    private final String name;

    private final List<String> roles;

    /**
     * Constructor for actor.
     *
     * @param name
     * @param roles
     */
    public Actor(String name, final List<String> roles) {
        this.name = name;
        this.roles = new ArrayList<>(roles);
    }

    public String getName() {
        return name;
    }

    public List<String> getRoles() {
        return roles;
    }
}
