package com.example.reservation.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;

public class LoggingRecordRecoverer implements ConsumerRecordRecoverer {

    private static final Logger log = LoggerFactory.getLogger(LoggingRecordRecoverer.class);

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        log.error("Giving up on {}-{}@{} after retries were exhausted: {}",
                record.topic(), record.partition(), record.offset(), exception.getMessage(), exception);
    }
}
