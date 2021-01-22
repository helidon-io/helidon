package io.helidon.lra.messaging;

import io.opentracing.Tracer;
import oracle.jms.AQjmsConstants;
import oracle.jms.AQjmsFactory;
import oracle.jms.AQjmsSession;
//import oracle.ucp.jdbc.PoolDataSource;
import javax.jms.*;
import javax.sql.DataSource;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;

public class AQOutgoing {

    @Inject
    @Named("coordinatordb")
    DataSource coordinatorDB;


    @Inject
    private Tracer tracer;

    String queueOwner = "orderuser", queueName = "lracoordinator";

    String updateDataAndSendEvent(
            DataSource dataSource, String orderid, String itemid, String deliverylocation) throws Exception {
        TopicSession session = null;
        try {
            TopicConnectionFactory q_cf = AQjmsFactory.getTopicConnectionFactory(dataSource);
            TopicConnection q_conn = q_cf.createTopicConnection();
            session = q_conn.createTopicSession(true, Session.CLIENT_ACKNOWLEDGE);
            Connection jdbcConnection = ((AQjmsSession) session).getDBConnection();
            System.out.println("updateDataAndSendEvent jdbcConnection:" + jdbcConnection + " about to insertOrderViaSODA...");
//            Order insertedOrder = insertOrderViaSODA(orderid, itemid, deliverylocation, jdbcConnection);
//            System.out.println("updateDataAndSendEvent insertOrderViaSODA complete about to send order message...");
            Topic topic = ((AQjmsSession) session).getTopic(queueOwner, queueName);
            System.out.println("updateDataAndSendEvent topic:" + topic);
            TextMessage objmsg = session.createTextMessage();
            TopicPublisher publisher = session.createPublisher(topic);
            objmsg.setIntProperty("Id", 1);
            objmsg.setIntProperty("Priority", 2);
//            String jsonString = JsonUtils.writeValueAsString(insertedOrder);
            String jsonString = "{}";
            objmsg.setText(jsonString);
            objmsg.setJMSCorrelationID("" + 1);
            objmsg.setJMSPriority(2);
            publisher.publish(topic, objmsg, DeliveryMode.PERSISTENT, 2, AQjmsConstants.EXPIRATION_NEVER);
            session.commit();
            System.out.println("updateDataAndSendEvent committed JSON order in database and sent message in the same tx with payload:" + jsonString);
            return topic.toString();
        } catch (Exception e) {
            System.out.println("updateDataAndSendEvent failed with exception:" + e +
                    " (will attempt rollback if session is not null) session:" + session);
            if (session != null) {
                try {
                    session.rollback();
                } catch (JMSException e1) {
                    System.out.println("updateDataAndSendEvent session.rollback() failed:" + e1);
                } finally {
                    throw e;
                }
            }
            throw e;
        } finally {
            if (session != null) session.close();
        }
    }


    public static void send(List<String> endpointURIs) {
        for (String endpointURI : endpointURIs) {
            String url = "jdbc:oracle:thin:@(description= (retry_count=20)(retry_delay=3)(address=(protocol=tcps)(port=1522)(host=adb.us-phoenix-1.oraclecloud.com))" +
                    "(connect_data=(service_name=mnisopbygm56hii_orderdb2_tp.atp.oraclecloud.com))" +
                    "(security=(ssl_server_cert_dn=\"CN=adwc.uscom-east-1.oraclecloud.com,OU=Oracle BMCS US,O=Oracle Corporation,L=Redwood City,ST=California,C=US\")))";
            Properties props = new Properties();
            props.setProperty("user", "ORDERUSER");
            props.setProperty("password", "Welcome12345");
            System.setProperty("oracle.jdbc.fanEnabled", "false");
            System.out.println("Messaging.send:" + endpointURI);
            QueueConnection queueConnection = null;
            try {
                DriverManager.registerDriver(new oracle.jdbc.OracleDriver());
                QueueConnectionFactory queueConnectionFactory = AQjmsFactory.getQueueConnectionFactory(url, props);
                queueConnection = queueConnectionFactory.createQueueConnection();
                QueueSession queueSession = queueConnection.createQueueSession(true, Session.CLIENT_ACKNOWLEDGE);
                Queue queue = ((AQjmsSession) queueSession).getQueue("orderuser", endpointURI);

                QueueSender sender = queueSession.createSender(queue);
                TextMessage msg = queueSession.createTextMessage();
//            String jsonString = JsonUtils.writeValueAsString(completionMessageJSON);
                String jsonString = endpointURI + "-LRAId";
                msg.setText(jsonString);
                sender.send(msg);
                queueSession.commit();
                System.out.println("Messaging.send:" + endpointURI + " finished");
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } finally {
                if (queueConnection != null) {
                    try {
                        queueConnection.close();
                    } catch (JMSException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
//    private Order insertOrderViaSODA(String orderid, String itemid, String deliverylocation,
//                                     Connection jdbcConnection)
//            throws OracleException {
//        Order order = new Order(orderid, itemid, deliverylocation, "pending", "", "");
//        new OrderDAO().create(jdbcConnection, order);
//        return order;
//    }
//
//    void updateOrderViaSODA(Order order, Connection jdbcConnection)
//            throws OracleException {
//        new OrderDAO().update(jdbcConnection, order);
//    }
//
//
//    String deleteOrderViaSODA( DataSource dataSource, String orderid) throws Exception {
//        new OrderDAO().delete(dataSource.getConnection(), orderid);
//        return "deleteOrderViaSODA success";
//    }
//
//
//    String dropOrderViaSODA( DataSource dataSource) throws Exception {
//        return new OrderDAO().drop(dataSource.getConnection());
//    }
//
//    Order getOrderViaSODA( DataSource dataSource, String orderid) throws Exception {
//        Connection jdbcConnection = dataSource.getConnection();
//        Order order = new OrderDAO().get(jdbcConnection, orderid);
//        jdbcConnection.close();
//        return order;
//    }

