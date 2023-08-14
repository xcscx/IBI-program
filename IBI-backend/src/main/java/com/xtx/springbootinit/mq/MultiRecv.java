package com.xtx.springbootinit.mq;

import com.rabbitmq.client.Channel;
        import com.rabbitmq.client.Connection;
        import com.rabbitmq.client.ConnectionFactory;
        import com.rabbitmq.client.DeliverCallback;

public class MultiRecv {

    private static final String TASK_QUEUE_NAME = "multi_queue";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        final Connection connection = factory.newConnection();

        final Channel channel = connection.createChannel();

        channel.queueDeclare(TASK_QUEUE_NAME, true, false, false, null);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        channel.basicQos(1);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            try {
                System.out.println(" [x] Received '" + message + "'");
                // 处理工作
                Thread.sleep(20000);
            }catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                System.out.println(" [x] Done");
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };
        // 执行消费，开启消费监听
        channel.basicConsume(TASK_QUEUE_NAME, false, deliverCallback, consumerTag -> { });
    }

}