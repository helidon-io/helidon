/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import io.helidon.config.Config;
import io.helidon.config.DeprecatedConfig;
import io.helidon.security.spi.AuditProvider;

/**
 * Default implementation of audit provider.
 */
final class DefaultAuditProvider implements AuditProvider {
    private final Logger auditLogger;
    private final Level failureLevel;
    private final Level successLevel;
    private final Level infoLevel;
    private final Level warnLevel;
    private final Level errorLevel;
    private final Level auditFailureLevel;

    private DefaultAuditProvider(Config config) {
        // config node is already located on the security node
        this.auditLogger = Logger.getLogger(DeprecatedConfig.get(config,
                                                                 "audit.defaultProvider.logger",
                                                                 "security.audit.defaultProvider.logger")
                                                    .asString()
                                                    .orElse("AUDIT"));

        this.failureLevel = level(config, "failure", Level.FINEST);
        this.successLevel = level(config, "success", Level.FINEST);
        this.infoLevel = level(config, "info", Level.FINEST);
        this.warnLevel = level(config, "warn", Level.WARNING);
        this.errorLevel = level(config, "error", Level.SEVERE);
        this.auditFailureLevel = level(config, "audit-failure", Level.SEVERE);
    }

    private Level level(Config config, String auditSeverity, Level defaultLevel) {
        return config.get("audit.defaultProvider.level." + auditSeverity)
                .asString()
                .map(Level::parse)
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
        Level level;

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

    private void logEvent(TracedAuditEvent event, Level level) {
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
