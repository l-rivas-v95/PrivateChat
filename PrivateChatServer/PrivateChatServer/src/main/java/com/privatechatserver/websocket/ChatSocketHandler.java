package com.privatechatserver.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> users = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {

        String user = getUserFromQuery(session.getUri());

        if (user != null) {
            users.put(user, session);
            System.out.println("Usuario conectado: " + user);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session,
                                     TextMessage message) throws Exception {

        String payload = message.getPayload();

        String to = extractValue(payload, "to");

        WebSocketSession destination = users.get(to);

        if (destination != null && destination.isOpen()) {

            destination.sendMessage(
                    new TextMessage(payload)
            );

        } else {

            session.sendMessage(
                    new TextMessage("Usuario offline: " + to)
            );
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      CloseStatus status) {

        users.values().remove(session);
    }

    private String getUserFromQuery(URI uri) {

        if (uri == null || uri.getQuery() == null) {
            return null;
        }

        for (String param : uri.getQuery().split("&")) {

            String[] parts = param.split("=");

            if (parts.length == 2 &&
                    parts[0].equals("user")) {

                return parts[1];
            }
        }

        return null;
    }

    private String extractValue(String json,
                                String key) {

        String search = "\"" + key + "\":\"";

        int start = json.indexOf(search);

        if (start == -1) {
            return null;
        }

        start += search.length();

        int end = json.indexOf("\"", start);

        if (end == -1) {
            return null;
        }

        return json.substring(start, end);
    }
}