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

package io.helidon.security.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.helidon.security.AuditEvent;

/**
 * A default implementation of AuditEvent, used by security itself.
 */
public final class SecurityAuditEvent implements AuditEvent {
    private final AuditSeverity severity;
    private final String eventType;
    private final String messageFormat;
    private final Optional<Throwable> throwable;
    private final List<AuditParam> auditParameters = new ArrayList<>();

    private SecurityAuditEvent(AuditSeverity severity,
                               String eventType,
                               String messageFormat,
                               Throwable throwable) {
        this.severity = severity;
        this.eventType = eventType;
        this.messageFormat = messageFormat;
        this.throwable = Optional.ofNullable(throwable);
    }

    /**
     * Factory method to create a security audit event.
     *
     * @param eventType     Type of event
     * @param messageFormat Format of event message
     * @return event instance
     */
    public static SecurityAuditEvent info(String eventType, String messageFormat) {
        return new SecurityAuditEvent(AuditSeverity.INFO, eventType, messageFormat, null);
    }

    /**
     * Factory method to create a security audit event.
     *
     * @param eventType     Type of event
     * @param messageFormat Format of event message
     * @return event instance
     */
    public static SecurityAuditEvent success(String eventType, String messageFormat) {
        return new SecurityAuditEvent(AuditSeverity.SUCCESS, eventType, messageFormat, null);
    }

    /**
     * Factory method to create a security audit event.
     *
     * @param eventType     Type of event
     * @param messageFormat Format of event message
     * @return event instance
     */
    public static SecurityAuditEvent warn(String eventType, String messageFormat) {
        return new SecurityAuditEvent(AuditSeverity.WARN, eventType, messageFormat, null);
    }

    /**
     * Factory method to create a security audit event.
     *
     * @param eventType     Type of event
     * @param messageFormat Format of event message
     * @param e             Throwable to be audited
     * @return event instance
     */
    public static SecurityAuditEvent warn(String eventType, String messageFormat, Throwable e) {
        return new SecurityAuditEvent(AuditSeverity.WARN, eventType, messageFormat, e);
    }

    /**
     * Factory method to create a security audit event.
     *
     * @param eventType     Type of event
     * @param messageFormat Format of event message
     * @return event instance
     */
    public static SecurityAuditEvent error(String eventType, String messageFormat) {
        return new SecurityAuditEvent(AuditSeverity.ERROR, eventType, messageFormat, null);
    }

    /**
     * Factory method to create a security audit event.
     *
     * @param eventType     Type of event
     * @param messageFormat Format of event message
     * @param e             Throwable to be audited
     * @return event instance
     */
    public static SecurityAuditEvent error(String eventType, String messageFormat, Throwable e) {
        return new SecurityAuditEvent(AuditSeverity.ERROR, eventType, messageFormat, e);
    }

    /**
     * Factory method to create a security audit event.
     *
     * @param eventType     Type of event
     * @param messageFormat Format of event message
     * @return event instance
     */
    public static SecurityAuditEvent failure(String eventType, String messageFormat) {
        return new SecurityAuditEvent(AuditSeverity.FAILURE, eventType, messageFormat, null);
    }

    /**
     * Factory method to create a security audit event.
     *
     * @param eventType     Type of event
     * @param messageFormat Format of event message
     * @param e             Throwable to be audited
     * @return event instance
     */
    public static SecurityAuditEvent failure(String eventType, String messageFormat, Throwable e) {
        return new SecurityAuditEvent(AuditSeverity.FAILURE, eventType, messageFormat, e);
    }

    /**
     * Factory method to create a security audit event.
     *
     * @param eventType     Type of event
     * @param messageFormat Format of event message
     * @return event instance
     */
    public static SecurityAuditEvent auditFailure(String eventType, String messageFormat) {
        return new SecurityAuditEvent(AuditSeverity.AUDIT_FAILURE, eventType, messageFormat, null);
    }

    /**
     * Factory method to create a security audit event.
     *
     * @param eventType     Type of event
     * @param messageFormat Format of event message
     * @param e             Throwable to be audited
     * @return event instance
     */
    public static SecurityAuditEvent auditFailure(String eventType, String messageFormat, Throwable e) {
        return new SecurityAuditEvent(AuditSeverity.AUDIT_FAILURE, eventType, messageFormat, e);
    }

    /**
     * Create a new audit event.
     *
     * @param severity      Severity of this event
     * @param eventType     Event type
     * @param messageFormat Message format to print this event out (default - may be overridden by {@link
     *                      io.helidon.security.spi.AuditProvider})
     * @return event instance
     */
    public static SecurityAuditEvent audit(AuditSeverity severity, String eventType, String messageFormat) {
        return new SecurityAuditEvent(severity, eventType, messageFormat, null);
    }

    /**
     * Add a parameter to this event.
     *
     * @param param parameter to add, e.g. using {@link AuditParam#plain(String, Object)}
     * @return updated instance
     */
    public SecurityAuditEvent addParam(AuditParam param) {
        this.auditParameters.add(param);
        return this;
    }

    @Override
    public String getEventType() {
        return eventType;
    }

    @Override
    public Optional<Throwable> getThrowable() {
        return throwable;
    }

    @Override
    public List<AuditParam> getParams() {
        return Collections.unmodifiableList(auditParameters);
    }

    @Override
    public String getMessageFormat() {
        return messageFormat;
    }

    @Override
    public AuditSeverity getSeverity() {
        return severity;
    }

    @Override
    public String toString() {
        return "AuditEvent{"
                + "severity=" + severity
                + ", eventType='" + eventType + '\''
                + ", messageFormat='" + messageFormat + '\''
                + ", throwable=" + throwable
                + ", auditParameters=" + toString(auditParameters)
                + '}';
    }

    private String toString(List<AuditParam> auditParameters) {
        StringBuilder response = new StringBuilder();

        for (AuditParam param : auditParameters) {
            if (param.isSensitive()) {
                response.append(param.getName()).append("=").append("********");
            } else {
                response.append(param.getName()).append("=").append(param.getValue().orElse("null"));
            }
            response.append(",");
        }

        if (response.length() != 0) {
            return response.substring(0, response.length() - 1);
        }

        return response.toString();
    }
}
