/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.tests.functional.context.hello;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import java.io.IOException;

/**
 * Presence of this interceptor in request scope tests that a request scope is
 * active after returning from the resource method call while running in a
 * different thread -- such as a fault tolerance thread.
 *
 * @see "https://github.com/oracle/helidon/issues/3804"
 * @see HelloResource#getHello
 */
@Provider
@RequestScoped
public class ResponseWriterInterceptor implements WriterInterceptor {

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
        context.proceed();
    }
}
