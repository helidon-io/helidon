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
 */

package io.helidon.microprofile.example.messaging.sse;

import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

/**
 * Example showing
 * <a href="https://github.com/eclipse/microprofile-reactive-messaging">Microprofile Reactive Messaging</a>
 * with <a href="https://github.com/eclipse/microprofile-reactive-streams-operators">Microprofile Reactive Stream Operators</a>
 * connected to <a href="https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest/sse.html">Server-Sent Events</a>.
 */
@ApplicationScoped
@ApplicationPath("/")
public class MessagingSseExampleApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(
                MessagingExampleResource.class
        );
    }
}
