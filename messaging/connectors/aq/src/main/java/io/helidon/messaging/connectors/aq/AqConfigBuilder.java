/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.messaging.ConnectorConfigBuilder;
import io.helidon.messaging.connectors.jms.AcknowledgeMode;
import io.helidon.messaging.connectors.jms.JmsConnector;
import io.helidon.messaging.connectors.jms.Type;

import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;

/**
 * Build AQ specific config.
 */
@Configured
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
    @ConfiguredOption
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
     * @param acknowledgeMode AUTO_ACKNOWLEDGE, CLIENT_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE
     * @return this builder
     */
    @ConfiguredOption("AUTO_ACKNOWLEDGE")
    public AqConfigBuilder acknowledgeMode(AcknowledgeMode acknowledgeMode) {
        super.property("acknowledge-mode", acknowledgeMode.name());
        return this;
    }

    /**
     * Select {@link jakarta.jms.ConnectionFactory ConnectionFactory}
     * in case factory is injected as a named bean or configured with name.
     *
     * @param factoryName connection factory name
     * @return this builder
     */
    @ConfiguredOption
    public AqConfigBuilder namedFactory(String factoryName) {
        super.property(JmsConnector.NAMED_FACTORY_ATTRIBUTE, factoryName);
        return this;
    }

    /**
     * Indicates whether the session will use a local transaction.
     *
     * @param transacted true if so
     * @return this builder
     */
    @ConfiguredOption("false")
    public AqConfigBuilder transacted(boolean transacted) {
        super.property("transacted", String.valueOf(transacted));
        return this;
    }

    /**
     * User name used for creating JMS connection.
     *
     * @param username JMS connection user name
     * @return this builder
     */
    @ConfiguredOption
    public AqConfigBuilder username(String username) {
        super.property("username", username);
        return this;
    }

    /**
     * Password used for creating JMS connection.
     *
     * @param password JMS connection password
     * @return this builder
     */
    @ConfiguredOption
    public AqConfigBuilder password(String password) {
        super.property("password", password);
        return this;
    }

    /**
     * Specify if connection is {@link io.helidon.messaging.connectors.jms.Type#QUEUE queue}
     * or {@link io.helidon.messaging.connectors.jms.Type#TOPIC topic}.
     *
     * @param type {@link io.helidon.messaging.connectors.jms.Type#QUEUE queue} or
     *             {@link io.helidon.messaging.connectors.jms.Type#TOPIC topic}
     * @return this builder
     */
    @ConfiguredOption("QUEUE")
    public AqConfigBuilder type(Type type) {
        super.property("type", type.toString());
        return this;
    }

    /**
     * Queue or topic name.
     *
     * @param destination queue or topic name
     * @return this builder
     */
    @ConfiguredOption
    public AqConfigBuilder destination(String destination) {
        super.property("destination", destination);
        return this;
    }

    /**
     * Use supplied destination name and {@link Type#QUEUE QUEUE} as type.
     *
     * @param destination queue name
     * @return this builder
     */
    @ConfiguredOption
    public AqConfigBuilder queue(String destination) {
        this.type(Type.QUEUE);
        this.destination(destination);
        return this;
    }

    /**
     * Use supplied destination name and {@link Type#TOPIC TOPIC} as type.
     *
     * @param destination topic name
     * @return this builder
     */
    @ConfiguredOption
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
     * <li>Example: NewsType = ’Sports’ OR NewsType = ’Opinion’</li>
     * </ul>
     *
     * @param messageSelector message selector expression
     * @return this builder
     */
    @ConfiguredOption
    public AqConfigBuilder messageSelector(String messageSelector) {
        super.property("message-selector", messageSelector);
        return this;
    }

    /**
     * Timeout for polling for next message in every poll cycle in millis.
     *
     * @param pollTimeout timeout of polling for next message
     * @return this builder
     */
    @ConfiguredOption("50")
    public AqConfigBuilder pollTimeout(long pollTimeout) {
        super.property("poll-timeout", String.valueOf(pollTimeout));
        return this;
    }

    /**
     * Period for executing poll cycles in millis.
     *
     * @param periodExecutions period for executing poll cycles in millis
     * @return this builder
     */
    @ConfiguredOption("100")
    public AqConfigBuilder periodExecutions(long periodExecutions) {
        super.property("period-executions", String.valueOf(periodExecutions));
        return this;
    }

    /**
     * When multiple channels share same session-group-id,
     * they share same JMS session.
     *
     * @param sessionGroupId identifier for channels sharing same JMS session
     * @return this builder
     */
    @ConfiguredOption
    public AqConfigBuilder sessionGroupId(String sessionGroupId) {
        super.property("session-group-id", sessionGroupId);
        return this;
    }

    /**
     * Client identifier for JMS connection.
     *
     * @param clientId client identifier for JMS connection
     * @return this builder
     */
    @ConfiguredOption
    public AqConfigBuilder clientId(String clientId) {
        super.property("client-id", clientId);
        return this;
    }

    /**
     * Indicates whether the consumer should be created as durable
     * (only relevant for topic destinations).
     *
     * @param durable {@code true} to create a durable consumer
     * @return this builder
     */
    @ConfiguredOption("false")
    public AqConfigBuilder durable(boolean durable) {
        super.property("durable", String.valueOf(durable));
        return this;
    }

    /**
     * Subscriber name used to identify a durable subscription.
     *
     * @param subscriberName name of the subscriber
     * @return this builder
     */
    @ConfiguredOption
    public AqConfigBuilder subscriberName(String subscriberName) {
        super.property("subscriber-name", subscriberName);
        return this;
    }

    /**
     * When set to {@code true}, messages published by this connection, or
     * any connection with the same client identifier, will not be delivered
     * to this durable subscription.
     *
     * @param nonLocal {@code true} to disable delivery of local messages
     * @return this builder
     */
    @ConfiguredOption("false")
    public AqConfigBuilder nonLocal(boolean nonLocal) {
        super.property("non-local", String.valueOf(nonLocal));
        return this;
    }
}
