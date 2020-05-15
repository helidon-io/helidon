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

package io.helidon.tests.integration.nativeimage.mp1;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class BeanClass {
    @Produces
    @RequestScoped
    public BeanType produceBeanType(@ConfigProperty(name = "app.message") String message) {
        return new BeanType(message);
    }

    public static class BeanType {
        private String message;

        // to support proxying
        public BeanType() {}

        public BeanType(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }
}
