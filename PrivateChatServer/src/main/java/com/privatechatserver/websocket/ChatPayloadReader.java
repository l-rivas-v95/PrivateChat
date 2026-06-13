package com.privatechatserver.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class ChatPayloadReader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ChatPayloadReader() {
    }

    static String value(String payload, String key) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(payload);
            JsonNode value = node.get(key);
            return value == null || value.isNull() ? null : value.asText();
        } catch (Exception exception) {
            return null;
        }
    }

    static String ack(String messageId) {
        try {
            return OBJECT_MAPPER
                    .createObjectNode()
                    .put("type", "ack")
                    .put("messageId", messageId)
                    .put("status", "DELIVERED")
                    .toString();
        } catch (Exception exception) {
            return "{\"type\":\"ack\",\"messageId\":\"" + messageId + "\",\"status\":\"DELIVERED\"}";
        }
    }
}
