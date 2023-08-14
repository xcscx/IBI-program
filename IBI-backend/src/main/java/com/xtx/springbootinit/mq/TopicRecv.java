package com.xtx.springbootinit.mq;

import com.rabbitmq.client.*;

public class TopicRecv {

    private static final String EXCHANGE_NAME = "topic_logs";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC);

        String queueName1 = "frontend_queue";
        channel.queueDeclare(queueName1, true, false, false, null);
        channel.queueBind(queueName1, EXCHANGE_NAME, "#.frontend.#");

        String queueName2 = "backend_queue";
        channel.queueDeclare(queueName2, true, false, false, null);
        channel.queueBind(queueName2, EXCHANGE_NAME, "#.backend.#");

        String queueName3 = "product_queue";
        channel.queueDeclare(queueName3, true, false, false, null);
        channel.queueBind(queueName3, EXCHANGE_NAME, "#.product.#");

        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        DeliverCallback deliverCallback1 = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [A_queue] Received '" + delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
        };
        DeliverCallback deliverCallback2 = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [B_queue] Received '" + delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
        };
        DeliverCallback deliverCallback3 = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [C_queue] Received '" + delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
        };

        channel.basicConsume(queueName1, true, deliverCallback1, consumerTag -> {
        });
        channel.basicConsume(queueName2, true, deliverCallback2, consumerTag -> {
        });
        channel.basicConsume(queueName3, true, deliverCallback3, consumerTag -> {
        });
        ////////////////////////

//        String queueName = channel.queueDeclare().getQueue();
//
//        if (argv.length < 1) {
//            System.err.println("Usage: ReceiveLogsTopic [binding_key]...");
//            System.exit(1);
//        }
//
//        for (String bindingKey : argv) {
//            channel.queueBind(queueName, EXCHANGE_NAME, bindingKey);
//        }
//
//        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
//
//        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
//            String message = new String(delivery.getBody(), "UTF-8");
//            System.out.println(" [x] Received '" + delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
//        };
//        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> { });
    }
}