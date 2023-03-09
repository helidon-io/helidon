/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.jms;

import io.helidon.messaging.connectors.jms.shim.JakartaJms;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static io.helidon.messaging.connectors.jms.AcknowledgeMode.AUTO_ACKNOWLEDGE;

public class AbstractJmsTest {

    static final String BROKER_URL = "vm://localhost?broker.persistent=false";
    static Session session;
    static ConnectionFactory connectionFactory;

    @BeforeAll
    static void beforeAll() throws Exception {
        connectionFactory = JakartaJms.create(new ActiveMQConnectionFactory(BROKER_URL));
        Connection connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, AcknowledgeMode.AUTO_ACKNOWLEDGE.getAckMode());
    }


    @AfterAll
    static void tearDown() throws Exception {
        session.close();
    }

    static void clearQueue(String queueName){
        var cf = JakartaJms.create(new ActiveMQConnectionFactory(AbstractJmsTest.BROKER_URL));
        try (Connection conn = cf.createConnection();
                var s = conn.createSession(false, AUTO_ACKNOWLEDGE.getAckMode())) {
            conn.start();
            Queue queue = s.createQueue(queueName);
            MessageConsumer cons = s.createConsumer(queue);
            jakarta.jms.Message m;
            do {
                m = cons.receive(100L);
            } while (m != null);
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }
}
