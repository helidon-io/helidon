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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.OptionalHelper;
import io.helidon.config.Config;
import io.helidon.security.spi.AuditProvider;

/**
 * Default implementation of audit provider.
 */
final class DefaultAuditProvider implements AuditProvider {
    private final Logger auditLogger;

    private DefaultAuditProvider(String loggerName) {
        this.auditLogger = Logger.getLogger(loggerName);
    }

    public static DefaultAuditProvider fromConfig(Config config) {
        return new DefaultAuditProvider(config.get("security.audit.defaultProvider.logger").asString("AUDIT"));
    }

    @Override
    public Consumer<TracedAuditEvent> getAuditConsumer() {
        return this::audit;
    }

    private void audit(TracedAuditEvent event) {
        String tracingId = event.getTracingId();
        switch (event.getSeverity()) {
        case FAILURE:
        case SUCCESS:
        case INFO:
            //trace info
            logEvent(tracingId, event, Level.FINEST);
            break;
        case WARN:
            //warning - something is not right, so let's log it
            logEvent(tracingId, event, Level.WARNING);
            break;
        case ERROR:
            //error - definitely a problem, log as severe
            logEvent(tracingId, event, Level.SEVERE);
            break;
        case AUDIT_FAILURE:
        default:
            //audit failure - something wrong with auditing mechanism...
            logEvent(tracingId, event, Level.SEVERE);
            break;
        }
    }

    private void logEvent(String tracingId, TracedAuditEvent event, Level level) {
        AuditSource auditSource = event.getAuditSource();

        StringBuilder locationInfo = new StringBuilder();
        locationInfo
                .append(auditSource.getClassName().orElse("UnknownClass"))
                .append(" ")
                .append(auditSource.getMethodName().orElse("UnknownMethod"))
                .append(" ")
                .append(auditSource.getFileName().orElse("UnknownFile"))
                .append(" ")
                .append(auditSource.getLineNumber().orElse(-1));

        // if the format here is changed, also change documentation of AuditProvider
        String msg = event.getSeverity()
                + " "
                + event.getEventType()
                + " "
                + tracingId
                + " "
                + event.getClass().getSimpleName()
                + " "
                + locationInfo
                + " :: \"" + formatMessage(event)
                + "\"";
        msg = msg.replace('\n', ' ');

        String finalMsg = msg;

        OptionalHelper.from(event.getThrowable())
                .ifPresentOrElse(throwable -> auditLogger.log(level,
                                                              finalMsg,
                                                              throwable), () -> auditLogger.log(level, finalMsg));
    }

    private String formatMessage(AuditEvent event) {
        return String.format(event.getMessageFormat(), toObjectParams(event.getParams()));
    }

    private Object[] toObjectParams(List<AuditEvent.AuditParam> parameters) {

        List<Object> result = new ArrayList<>();

        for (AuditEvent.AuditParam param : parameters) {
            if (param.isSensitive()) {
                result.add(param.getName() + " (sensitive)");
            } else {
                result.add(param.getValue().orElse("null"));
            }
        }

        return result.toArray(new Object[0]);
    }
}
