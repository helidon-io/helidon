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

package io.helidon.webserver.testing.junit5.http3;

import java.lang.reflect.Proxy;

import io.helidon.webclient.http3.Http3Client;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3ServerExtensionTest {
    private static final ParameterContext PARAMETER_CONTEXT = unusedCollaborator(ParameterContext.class);
    private static final ExtensionContext EXTENSION_CONTEXT = unusedCollaborator(ExtensionContext.class);
    private static final WebServer SERVER = unusedCollaborator(WebServer.class);

    private final Http3ServerExtension extension = new Http3ServerExtension();

    @Test
    void beforeAllRejectsNullContext() {
        assertThrows(NullPointerException.class, () -> extension.beforeAll(null));
    }

    @Test
    void supportsParameterRejectsNullArgumentsBeforeUsingContexts() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                                   () -> extension.supportsParameter(null, EXTENSION_CONTEXT)),
                () -> assertThrows(NullPointerException.class,
                                   () -> extension.supportsParameter(PARAMETER_CONTEXT, null)));
    }

    @Test
    void resolveParameterRejectsNullArgumentsBeforeUsingCollaborators() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                                   () -> extension.resolveParameter(null, EXTENSION_CONTEXT, Http3Client.class, SERVER)),
                () -> assertThrows(NullPointerException.class,
                                   () -> extension.resolveParameter(PARAMETER_CONTEXT, null, Http3Client.class, SERVER)),
                () -> assertThrows(NullPointerException.class,
                                   () -> extension.resolveParameter(PARAMETER_CONTEXT, EXTENSION_CONTEXT, null, SERVER)),
                () -> assertThrows(NullPointerException.class,
                                   () -> extension.resolveParameter(PARAMETER_CONTEXT, EXTENSION_CONTEXT, Http3Client.class, null)));
    }

    @Test
    void updateListenerBuilderRejectsNullArguments() {
        ListenerConfig.Builder listenerBuilder = ListenerConfig.builder();
        Router.Builder routerBuilder = Router.builder();

        assertAll(
                () -> assertThrows(NullPointerException.class,
                                   () -> extension.updateListenerBuilder(null, listenerBuilder, routerBuilder)),
                () -> assertThrows(NullPointerException.class,
                                   () -> extension.updateListenerBuilder("test", null, routerBuilder)),
                () -> assertThrows(NullPointerException.class,
                                   () -> extension.updateListenerBuilder("test", listenerBuilder, null)));
    }

    @Test
    void afterEachRejectsNullContext() {
        assertThrows(NullPointerException.class, () -> extension.afterEach(null));
    }

    private static <T> T unusedCollaborator(Class<T> type) {
        // Null arguments must be rejected before consulting any otherwise valid collaborator.
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (_, method, _) -> {
            throw new AssertionError("Unexpected collaborator invocation: " + method.getName());
        }));
    }
}
