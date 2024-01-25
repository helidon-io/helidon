/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.mp.reactivemessaging;

import java.util.concurrent.CompletionStage;

import javax.jms.JMSException;

import io.helidon.config.Config;
import io.helidon.messaging.connectors.aq.AqMessage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import oracle.jms.AQjmsConnectionFactory;
import oracle.jms.AQjmsQueueConnectionFactory;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;

@SuppressWarnings("ALL")
class AqSnippets {

    static final Config config = Config.global();

    // tag::snippet_1[]
    @Produces
    @ApplicationScoped
    @Named("aq-orderdb-factory")
    public AQjmsConnectionFactory connectionFactory() throws JMSException {
        AQjmsQueueConnectionFactory fact = new AQjmsQueueConnectionFactory();
        fact.setJdbcURL(config.get("jdbc.url").asString().get());
        fact.setUsername(config.get("jdbc.user").asString().get());
        fact.setPassword(config.get("jdbc.pass").asString().get());
        return fact;
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @Incoming("from-aq")
    public void consumeAq(String msg) {
        System.out.println("Oracle AQ says: " + msg);
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    @Incoming("from-aq")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public CompletionStage<?> consumeAq(AqMessage<String> msg) {
        // direct commit
        //msg.getDbConnection().commit();
        System.out.println("Oracle AQ says: " + msg.getPayload());
        // ack commits only in non-transacted mode
        return msg.ack();
    }
    // end::snippet_3[]

    // tag::snippet_4[]
    @Outgoing("to-aq")
    public PublisherBuilder<String> produceToAq() {
        return ReactiveStreams.of("test1", "test2");
    }
    // end::snippet_4[]

}
