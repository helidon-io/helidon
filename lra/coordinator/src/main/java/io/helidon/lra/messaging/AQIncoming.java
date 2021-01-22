package io.helidon.lra.messaging;

import oracle.jms.AQjmsConsumer;
import oracle.jms.AQjmsFactory;
import oracle.jms.AQjmsSession;

import javax.inject.Inject;
import javax.inject.Named;
import javax.jms.*;
import javax.sql.DataSource;

public class AQIncoming {

    @Inject
    @Named("coordinatordb")
    DataSource coordinatorDB;

    String queueOwner = "orderuser", queueName = "lracoordinator";

    public void dolistenForMessages() throws JMSException {
        QueueConnectionFactory q_cf = AQjmsFactory.getQueueConnectionFactory(coordinatorDB);
        QueueSession qsess = null;
        QueueConnection qconn = null;
        AQjmsConsumer consumer = null;
        boolean done = false;
        while (!done) {
            try {
                if (qconn == null || qsess == null) {
                    qconn = q_cf.createQueueConnection();
                    qsess = qconn.createQueueSession(true, Session.CLIENT_ACKNOWLEDGE);
                    qconn.start();
                    Queue queue = ((AQjmsSession) qsess).getQueue(queueOwner, queueName);
                    consumer = (AQjmsConsumer) qsess.createConsumer(queue);
                }
                TextMessage textMessage = (TextMessage) consumer.receive(-1);
                String messageText = textMessage.getText();
//                System.out.println("messageText " + messageText);
////                    System.out.println("Priority: " + textMessage.getIntProperty("Priority"));
//                System.out.print(" Pri: " + textMessage.getJMSPriority());
////                    System.out.print(" Message: " + textMessage.getIntProperty("Id"));
//                Inventory inventory = JsonUtils.read(messageText, Inventory.class);
//                String orderid = inventory.getOrderid();
//                String itemid = inventory.getItemid();
//                String inventorylocation = inventory.getInventorylocation();
//                System.out.println("Lookup orderid:" + orderid);
//                Order order = orderResource.orderServiceEventProducer.getOrderViaSODA(orderResource.atpOrderPdb, orderid);
//                if (order == null)
//                    throw new JMSException("Rollingback message as no orderDetail found for orderid:" + orderid +
//                            ". It may have been started by another server (eg if horizontally scaling) or " +
//                            " this server started the order but crashed. ");
//                boolean isSuccessfulInventoryCheck = !(inventorylocation == null || inventorylocation.equals("")
//                        || inventorylocation.equals("inventorydoesnotexist")
//                        || inventorylocation.equals("none"));
//                if (isSuccessfulInventoryCheck) {
//                    order.setStatus("success inventory exists");
//                    order.setInventoryLocation(inventorylocation);
//                    order.setSuggestiveSale(inventory.getSuggestiveSale());
//                } else {
//                    order.setStatus("failed inventory does not exist");
//                }
//                dbConnection = ((AQjmsSession) qsess).getDBConnection();
//                orderResource.orderServiceEventProducer.updateOrderViaSODA(order, dbConnection);
//                System.out.println("((AQjmsSession) qsess).getDBConnection(): " + ((AQjmsSession) qsess).getDBConnection());
                qsess.commit();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exception in receiveMessages: " + e);
                qsess.rollback();
            }
        }
    }
}
