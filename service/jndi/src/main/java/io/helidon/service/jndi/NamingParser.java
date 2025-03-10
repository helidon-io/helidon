/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.service.jndi;

import java.util.Collections;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.naming.CompoundName;
import javax.naming.Name;
import javax.naming.NameParser;
import javax.naming.NamingException;

class NamingParser implements NameParser {
    private final Properties syntax = new Properties();

    @Override
    public Name parse(String name) throws NamingException {
        return new CompoundName(name, syntax);
    }

    private static class NamingName extends CompoundName {
        private NamingName(String name, Properties syntax) {
            super(Collections.enumeration(Stream.of(name.split("[./]")).collect(Collectors.toUnmodifiableList())),
                  syntax);
        }
    }
}
