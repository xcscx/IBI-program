package com.xtx.springbootinit.bizmq;

import com.xtx.springbootinit.constant.BiMqConstant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MyMessageProducerTest {

    @Resource
    private MyMessageProducer myMessageProducer;

    /**
     * 发送消息方法
     * @param message
     */
    @Test
    void sendMessage(String message) {
        myMessageProducer.sendMessage(message);
    }
}