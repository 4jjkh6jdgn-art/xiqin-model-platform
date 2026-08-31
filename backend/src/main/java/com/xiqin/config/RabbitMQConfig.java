package com.xiqin.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.queue-model-process}")
    private String queueModelProcess;

    @Value("${app.rabbitmq.queue-file-process}")
    private String queueFileProcess;

    @Value("${app.rabbitmq.routing-model-process}")
    private String routingModelProcess;

    @Value("${app.rabbitmq.routing-file-process}")
    private String routingFileProcess;

    @Bean
    public TopicExchange xiqinExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue modelProcessQueue() {
        return QueueBuilder.durable(queueModelProcess).build();
    }

    @Bean
    public Queue fileProcessQueue() {
        return QueueBuilder.durable(queueFileProcess).build();
    }

    @Bean
    public Binding modelProcessBinding() {
        return BindingBuilder.bind(modelProcessQueue()).to(xiqinExchange()).with(routingModelProcess);
    }

    @Bean
    public Binding fileProcessBinding() {
        return BindingBuilder.bind(fileProcessQueue()).to(xiqinExchange()).with(routingFileProcess);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
