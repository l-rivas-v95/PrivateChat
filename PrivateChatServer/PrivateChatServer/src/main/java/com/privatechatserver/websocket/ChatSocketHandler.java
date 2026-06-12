package com.privatechatserver.websocket;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import org.springframework.stereotype.Component;

@Component
public class ChatSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> users = new ConcurrentHashMap<>();
    private final Map<String, List<String>> pendingMessages = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String user = getUserFromQuery(session.getUri());

        if (user == null || user.isBlank()) {
            session.close();
            return;
        }

        users.put(user, session);
        System.out.println("Usuario conectado: " + user);

        List<String> pending = pendingMessages.remove(user);

        if (pending != null) {
            for (String message : pending) {
                session.sendMessage(new TextMessage(message));
            }

            System.out.println("Mensajes pendientes entregados a " + user + ": " + pending.size());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        String to = extractValue(payload, "to");

        if (to == null || to.isBlank()) {
            session.sendMessage(new TextMessage("Error: destinatario no encontrado"));
            return;
        }

        WebSocketSession receiverSession = users.get(to);

        if (receiverSession != null && receiverSession.isOpen()) {
            receiverSession.sendMessage(new TextMessage(payload));
            return;
        }

        pendingMessages
                .computeIfAbsent(to, key -> new ArrayList<>())
                .add(payload);

        System.out.println("Mensaje pendiente guardado para: " + to);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        users.entrySet().removeIf(entry -> entry.getValue().getId().equals(session.getId()));
        System.out.println("Usuario desconectado");
    }

    private String getUserFromQuery(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }

        String query = uri.getQuery();

        for (String param : query.split("&")) {
            String[] parts = param.split("=");

            if (parts.length == 2 && parts[0].equals("user")) {
                return parts[1];
            }
        }

        return null;
    }

    private String extractValue(String json, String key) {
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