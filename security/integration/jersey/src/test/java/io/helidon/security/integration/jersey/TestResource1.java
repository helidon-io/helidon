/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.integration.jersey;

import java.lang.reflect.Proxy;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.helidon.security.SecurityContext;
import io.helidon.security.annotations.Authenticated;

import org.glassfish.jersey.server.Uri;

/**
 * Test resource 1.
 */
@Path("/test1")
public class TestResource1 {
    @Uri("http://localhost:9998/test2")
    private WebTarget target;

    @GET
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public TransferObject getIt(@Context SecurityContext context) {
        TransferObject fromTest2 = target.request()
                .get(TransferObject.class);

        // we expect this NOT to be a proxy
        boolean param = Proxy.isProxyClass(context.getClass());
        String className = context.getClass().getName();

        fromTest2.setParam(param);
        fromTest2.setParamClass(className);

        return fromTest2;
    }

    public static class TransferObject {
        private String subject;
        private boolean field;
        private boolean param;
        private String fieldClass;
        private String paramClass;

        public TransferObject() {
        }

        private TransferObject(String subject, boolean field, boolean param, String fieldClass, String paramClass) {
            this.subject = subject;
            this.field = field;
            this.param = param;
            this.fieldClass = fieldClass;
            this.paramClass = paramClass;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public boolean isField() {
            return field;
        }

        public void setField(boolean field) {
            this.field = field;
        }

        public boolean isParam() {
            return param;
        }

        public void setParam(boolean param) {
            this.param = param;
        }

        public String getFieldClass() {
            return fieldClass;
        }

        public void setFieldClass(String fieldClass) {
            this.fieldClass = fieldClass;
        }

        public String getParamClass() {
            return paramClass;
        }

        public void setParamClass(String paramClass) {
            this.paramClass = paramClass;
        }
    }
}
