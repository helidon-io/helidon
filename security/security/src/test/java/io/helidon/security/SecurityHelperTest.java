/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link Security}'s static helper methods.
 */
public class SecurityHelperTest {
    @Test
    public void testRoles() {
        Subject subject = Subject.builder()
                .principal(Principal.create("etcd_service"))
                .addGrant(Role.create("etcd_role"))
                .build();
        Set<String> roles = Security.getRoles(subject);

        assertThat(roles, hasItem("etcd_role"));
    }
}
