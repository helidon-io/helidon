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

package io.helidon.common.uri;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class UriQueryDecodedNameCopySecurityTest {
    @Test
    void copyMustNotTurnEncodedAmpersandIntoAnotherParameter() {
        UriQuery decorated = (UriQuery) Proxy.newProxyInstance(
                UriQuery.class.getClassLoader(),
                new Class<?>[] {UriQuery.class},
                (_, method, args) -> switch (method.getName()) {
                    case "rawValue" -> "safe%26admin=true";
                    case "names" -> Set.of("safe&admin");
                    case "getAllRaw", "all" -> {
                        if ("safe&admin".equals(args[0])) {
                            yield List.of("true");
                        }
                        throw new NoSuchElementException();
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        assertThat(UriQueryWriteable.create().from(decorated).rawValue(), is("safe%26admin=true"));
    }
}
