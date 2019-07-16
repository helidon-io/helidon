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
package io.helidon.microprofile.accesslog;

import javax.annotation.Priority;

import io.helidon.config.Config;
import io.helidon.microprofile.server.spi.MpService;
import io.helidon.microprofile.server.spi.MpServiceContext;
import io.helidon.webserver.accesslog.AccessLogSupport;

/**
 * Extension of MicroProfile to add support for access log.
 */
@Priority(9)
public class MpAccessLogService implements MpService {
    @Override
    public void configure(MpServiceContext context) {
        Config accessLogConfig = context.helidonConfig().get("server.access-log");

        AccessLogSupport accessLogSupport = AccessLogSupport.create(accessLogConfig);

        context.serverRoutingBuilder().register(accessLogSupport);
    }
}
