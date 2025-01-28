package com.codesupreme.sifarisqrupu.dto.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ForwardedMessageConverter implements AttributeConverter<ForwardedMessage, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(ForwardedMessage attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert ForwardedMessage to JSON", e);
        }
    }

    @Override
    public ForwardedMessage convertToEntityAttribute(String dbData) {
        try {
            return objectMapper.readValue(dbData, ForwardedMessage.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JSON to ForwardedMessage", e);
        }
    }
}

