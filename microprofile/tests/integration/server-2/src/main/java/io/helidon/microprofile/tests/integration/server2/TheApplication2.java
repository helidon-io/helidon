/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.microprofile.tests.integration.server2;

import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import io.helidon.common.CollectionsHelper;

/**
 * Application class.
 */
// this application is not @ApplicationScoped, so it is ignored by Server
@ApplicationPath("/application")
public class TheApplication2 extends Application {
    @Override
    public Set<Class<?>> getClasses() {
        return CollectionsHelper.setOf(TheResource2First.class,
                                       TheResource2Second.class,
                                       TheProvider2.class);
    }
}
