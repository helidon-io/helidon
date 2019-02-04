/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.guides.mp.restfulwebservice;

import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import io.helidon.common.CollectionsHelper;
/**
 * Simple Application that produces a greeting message.
 */
// tag::greetAppBody[]
@ApplicationScoped // <1>
@ApplicationPath("/") // <2>
public class GreetApplication extends Application { // <3>

    // tag::getClassesMethod[]
    @Override
    public Set<Class<?>> getClasses() {
        return CollectionsHelper.setOf(GreetResource.class); // <4>
    }
    // end::getClassesMethod[]
}
// end::greetAppBody[]
