package com.sysco.inventoryservice.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class SagaMessageSerializer {

    private final ObjectMapper objectMapper;

    public SagaMessageSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(SagaMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize saga message", ex);
        }
    }

    public SagaMessage fromJson(String rawMessage) {
        try {
            return objectMapper.readValue(rawMessage, SagaMessage.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize saga message", ex);
        }
    }
}
