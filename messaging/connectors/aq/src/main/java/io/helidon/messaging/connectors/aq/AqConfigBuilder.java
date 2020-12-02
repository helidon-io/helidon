/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.aq;

import io.helidon.messaging.ConnectorConfigBuilder;
import io.helidon.messaging.connectors.jms.AcknowledgeMode;
import io.helidon.messaging.connectors.jms.Type;

import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;

/**
 * Build AQ specific config.
 */
public class AqConfigBuilder extends ConnectorConfigBuilder {

    AqConfigBuilder() {
        super();
        super.property(ConnectorFactory.CONNECTOR_ATTRIBUTE, AqConnector.CONNECTOR_NAME);
    }

    /**
     * Add custom property.
     *
     * @param key   property key
     * @param value property value
     * @return this builder
     */
    public AqConfigBuilder property(String key, String value) {
        super.property(key, value);
        return this;
    }

    /**
     * Mapping to {@link javax.sql.DataSource DataSource} supplied with
     * {@link io.helidon.messaging.connectors.aq.AqConnector.AqConnectorBuilder#dataSource(String, javax.sql.DataSource)
     * AqConnectorBuilder.dataSource()}.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param dataSourceName data source identifier
     * @return this builder
     */
    public AqConfigBuilder dataSource(String dataSourceName) {
        super.property(AqConnector.DATASOURCE_ATTRIBUTE, dataSourceName);
        return this;
    }

    /**
     * JMS acknowledgement mode.
     * <ul>
     * <li><b>AUTO_ACKNOWLEDGE</b> Acknowledges automatically after message reception over JMS api.</li>
     * <li><b>CLIENT_ACKNOWLEDGE</b> Message is acknowledged when
     * {@link org.eclipse.microprofile.reactive.messaging.Message#ack Message.ack()} is invoked either manually or
     * by {@link org.eclipse.microprofile.reactive.messaging.Acknowledgment Acknowledgment} policy.</li>
     * <li><b>DUPS_OK_ACKNOWLEDGE</b> Messages are acknowledged lazily which can result in
     * duplicate messages being delivered.</li>
     * </ul>
     *
     * <ul>
     * <li>Type: enum</li>
     * <li>Default: AUTO_ACKNOWLEDGE</li>
     * <li>Valid Values: AUTO_ACKNOWLEDGE, CLIENT_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE</li>
     * </ul>
     *
     * @param acknowledgeMode AUTO_ACKNOWLEDGE, CLIENT_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE
     * @return this builder
     */
    public AqConfigBuilder acknowledgeMode(AcknowledgeMode acknowledgeMode) {
        super.property("acknowledge-mode", acknowledgeMode.name());
        return this;
    }

    /**
     * Indicates whether the session will use a local transaction.
     *
     * <ul>
     * <li>Type: boolean</li>
     * <li>Default: false</li>
     * <li>Valid Values: true, false</li>
     * </ul>
     *
     * @param transacted true if so
     * @return this builder
     */
    public AqConfigBuilder transacted(boolean transacted) {
        super.property("transacted", String.valueOf(transacted));
        return this;
    }

    /**
     * User name used for creating JMS connection.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param username JMS connection user name
     * @return this builder
     */
    public AqConfigBuilder username(String username) {
        super.property("username", username);
        return this;
    }

    /**
     * Password used for creating JMS connection.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param password JMS connection password
     * @return this builder
     */
    public AqConfigBuilder password(String password) {
        super.property("password", password);
        return this;
    }

    /**
     * Specify if connection is {@link io.helidon.messaging.connectors.jms.Type#QUEUE queue}
     * or {@link io.helidon.messaging.connectors.jms.Type#TOPIC topic}.
     *
     * <ul>
     * <li>Type: enum</li>
     * <li>Default: {@link io.helidon.messaging.connectors.jms.Type#QUEUE QUEUE}</li>
     * <li>Valid Values: {@link io.helidon.messaging.connectors.jms.Type#QUEUE QUEUE},
     * {@link io.helidon.messaging.connectors.jms.Type#TOPIC TOPIC}</li>
     * </ul>
     *
     * @param type {@link io.helidon.messaging.connectors.jms.Type#QUEUE queue} or
     *             {@link io.helidon.messaging.connectors.jms.Type#TOPIC topic}
     * @return this builder
     */
    public AqConfigBuilder type(Type type) {
        super.property("type", type.toString());
        return this;
    }

    /**
     * Queue or topic name.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param destination queue or topic name
     * @return this builder
     */
    public AqConfigBuilder destination(String destination) {
        super.property("destination", destination);
        return this;
    }

    /**
     * Use supplied destination name and {@link Type#QUEUE QUEUE} as type.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param destination queue name
     * @return this builder
     */
    public AqConfigBuilder queue(String destination) {
        this.type(Type.QUEUE);
        this.destination(destination);
        return this;
    }

    /**
     * Use supplied destination name and {@link Type#TOPIC TOPIC} as type.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param destination topic name
     * @return this builder
     */
    public AqConfigBuilder topic(String destination) {
        this.type(Type.TOPIC);
        this.destination(destination);
        return this;
    }

    /**
     * JMS API message selector expression based on a subset of the SQL92.
     * Expression can only access headers and properties, not the payload.
     *
     * <ul>
     * <li>Type: string</li>
     * <li>Example: NewsType = ’Sports’ OR NewsType = ’Opinion’</li>
     * </ul>
     *
     * @param messageSelector message selector expression
     * @return this builder
     */
    public AqConfigBuilder messageSelector(String messageSelector) {
        super.property("message-selector", messageSelector);
        return this;
    }

    /**
     * Timeout for polling for next message in every poll cycle in millis.
     *
     * <ul>
     * <li>Type: milliseconds</li>
     * <li>Default: 50</li>
     * </ul>
     *
     * @param pollTimeout timeout of polling for next message
     * @return this builder
     */
    public AqConfigBuilder pollTimeout(long pollTimeout) {
        super.property("poll-timeout", String.valueOf(pollTimeout));
        return this;
    }

    /**
     * Period for executing poll cycles in millis.
     *
     * <ul>
     * <li>Type: milliseconds</li>
     * <li>Default: 100</li>
     * </ul>
     *
     * @param periodExecutions period for executing poll cycles in millis
     * @return this builder
     */
    public AqConfigBuilder periodExecutions(long periodExecutions) {
        super.property("period-executions", String.valueOf(periodExecutions));
        return this;
    }

    /**
     * When multiple channels share same session-group-id,
     * they share same JMS session.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param sessionGroupId identifier for channels sharing same JMS session
     * @return this builder
     */
    public AqConfigBuilder sessionGroupId(String sessionGroupId) {
        super.property("session-group-id", sessionGroupId);
        return this;
    }
}
