/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

import io.helidon.common.config.Config;
import io.helidon.security.spi.AuditProvider;

/**
 * Default implementation of audit provider.
 */
final class DefaultAuditProvider implements AuditProvider {
    private final System.Logger auditLogger;
    private final System.Logger.Level failureLevel;
    private final System.Logger.Level successLevel;
    private final System.Logger.Level infoLevel;
    private final System.Logger.Level warnLevel;
    private final System.Logger.Level errorLevel;
    private final System.Logger.Level auditFailureLevel;

    private DefaultAuditProvider(Config config) {
        // config node is already located on the security node
        this.auditLogger = System.getLogger(config.get("audit.defaultProvider.logger")
                                                    .asString()
                                                    .orElse("AUDIT"));

        this.failureLevel = level(config, "failure", System.Logger.Level.TRACE);
        this.successLevel = level(config, "success", System.Logger.Level.TRACE);
        this.infoLevel = level(config, "info", System.Logger.Level.TRACE);
        this.warnLevel = level(config, "warn", System.Logger.Level.WARNING);
        this.errorLevel = level(config, "error", System.Logger.Level.ERROR);
        this.auditFailureLevel = level(config, "audit-failure", System.Logger.Level.ERROR);
    }

    private System.Logger.Level level(Config config, String auditSeverity, System.Logger.Level defaultLevel) {
        return config.get("audit.defaultProvider.level." + auditSeverity)
                .asString()
                .map(s -> System.Logger.Level.valueOf(s))
                .orElse(defaultLevel);
    }

    static DefaultAuditProvider create(Config config) {
        return new DefaultAuditProvider(config);
    }

    @Override
    public Consumer<TracedAuditEvent> auditConsumer() {
        return this::audit;
    }

    private void audit(TracedAuditEvent event) {
        System.Logger.Level level;

        switch (event.severity()) {
        case FAILURE:
            level = failureLevel;
            break;
        case SUCCESS:
            level = successLevel;
            break;
        case INFO:
            level = infoLevel;
            break;
        case WARN:
            level = warnLevel;
            break;
        case ERROR:
            level = errorLevel;
            break;
        case AUDIT_FAILURE:
        default:
            //audit failure - something wrong with auditing mechanism...
            level = auditFailureLevel;
            break;
        }

        logEvent(event, level);
    }

    private void logEvent(TracedAuditEvent event, System.Logger.Level level) {
        if (!auditLogger.isLoggable(level)) {
            // no need to create the message when the message would not be logged
            return;
        }

        AuditSource auditSource = event.auditSource();

        StringBuilder locationInfo = new StringBuilder();
        locationInfo
                .append(auditSource.className().orElse("UnknownClass"))
                .append(" ")
                .append(auditSource.methodName().orElse("UnknownMethod"))
                .append(" ")
                .append(auditSource.fileName().orElse("UnknownFile"))
                .append(" ")
                .append(auditSource.lineNumber().orElse(-1));

        // if the format here is changed, also change documentation of AuditProvider
        String msg = event.severity()
                + " "
                + event.eventType()
                + " "
                + event.tracingId()
                + " "
                + event.getClass().getSimpleName()
                + " "
                + locationInfo
                + " :: \"" + formatMessage(event)
                + "\"";
        msg = msg.replace('\n', ' ');

        String finalMsg = msg;

        event.throwable()
                .ifPresentOrElse(throwable -> auditLogger.log(level,
                                                              finalMsg,
                                                              throwable), () -> auditLogger.log(level, finalMsg));
    }

    String formatMessage(AuditEvent event) {
        try {
            return String.format(event.messageFormat(), toObjectParams(event.params()));
        } catch (Exception e) {
            // problem with the format
            return "Formatting failed for format: " + event.messageFormat() + ", parameters: " + event.params();
        }
    }

    private Object[] toObjectParams(List<AuditEvent.AuditParam> parameters) {

        List<Object> result = new ArrayList<>();

        for (AuditEvent.AuditParam param : parameters) {
            if (param.isSensitive()) {
                result.add("(sensitive)");
            } else {
                result.add(param.value().orElse("null"));
            }
        }

        return result.toArray(new Object[0]);
    }
}
