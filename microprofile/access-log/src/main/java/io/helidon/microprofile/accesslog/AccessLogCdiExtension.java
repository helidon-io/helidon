/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.microprofile.accesslog;

import javax.annotation.Priority;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;

import io.helidon.config.Config;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.accesslog.AccessLogSupport;

import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * Extension of MicroProfile to add support for access log.
 */
public class AccessLogCdiExtension implements Extension {
    private void setUpAccessLog(@Observes @Priority(PLATFORM_BEFORE + 10) @RuntimeStart Config config,
                                BeanManager beanManager) {
        Config alConfig = config.get("server.access-log");
        AccessLogSupport accessLogSupport = AccessLogSupport.create(alConfig);

        beanManager.getExtension(ServerCdiExtension.class)
                .serverRoutingBuilder()
                .register(accessLogSupport);
    }
}
