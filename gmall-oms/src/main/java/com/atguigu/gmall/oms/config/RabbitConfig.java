package com.atguigu.gmall.oms.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {

    // 普通交换机X：ORDER-EXCHANGE

    // 延时队列 TTLQ
    @Bean
    public Queue ttlQueue(){
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-message-ttl", 120000);
        arguments.put("x-dead-letter-exchange", "ORDER-EXCHANGE");
        arguments.put("x-dead-letter-routing-key", "order.dead");
        return new Queue("ORDER-TTL-QUEUE", true, false, false, arguments);
    }

    // 把延时队列TTLQ绑定到交换机X
    @Bean
    public Binding ttlBinding(){
        return new Binding("ORDER-TTL-QUEUE", Binding.DestinationType.QUEUE,
                "ORDER-EXCHANGE", "order.ttl", null);
    }

    // 死信交换机 DLX  :ORDER-EXCHANGE

    // 死信队列DLQ
//    @Bean
//    public Queue dlQueue(){
//        return new Queue("ORDER-DEAD-QUEUE", true, false, false, null);
//    }

    // 把死信队列DLQ绑定到死信交换机（DLX）
//    @Bean
//    public Binding dlBinding(){
//        return new Binding("ORDER-DEAD-QUEUE", Binding.DestinationType.QUEUE, "ORDER-EXCHANGE", "order.dead", null);
//    }
}
