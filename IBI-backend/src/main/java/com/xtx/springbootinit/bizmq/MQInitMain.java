package com.xtx.springbootinit.bizmq;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.xtx.springbootinit.constant.BiMqConstant;

/**
 * 用于创建程序使用的交换机和队列
 */
public class MQInitMain {

    public static void main(String[] args) {

        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            channel.exchangeDeclare(BiMqConstant.BI_EXCHANGE_NAME, BuiltinExchangeType.DIRECT);

            channel.queueDeclare(BiMqConstant.BI_QUEUE_NAME, true, false, false, null);
            channel.queueBind(BiMqConstant.BI_QUEUE_NAME, BiMqConstant.BI_EXCHANGE_NAME, "routingKey");
        }catch (Exception e) {

        }


    }

}
