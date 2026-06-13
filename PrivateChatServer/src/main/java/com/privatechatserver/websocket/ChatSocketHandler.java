package com.privatechatserver.websocket;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ChatSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> users = new ConcurrentHashMap<>();
    private final Map<String, List<String>> pendingMessages = new ConcurrentHashMap<>();
    private final Map<String, List<String>> pendingAcks = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String user = getUserFromQuery(session.getUri());

        if (user == null || user.isBlank()) {
            session.close();
            return;
        }

        users.put(user, session);
        System.out.println("Usuario conectado: " + user);

        deliverPendingMessages(user, session);
        deliverPendingAcks(user, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        String to = ChatPayloadReader.value(payload, "to");

        if (to == null || to.isBlank()) {
            safeSend(session, "Error: destinatario no encontrado");
            return;
        }

        WebSocketSession receiverSession = users.get(to);

        if (receiverSession != null && receiverSession.isOpen()) {
            if (safeSend(receiverSession, payload)) {
                sendOrQueueAck(payload);
            }
            return;
        }

        pendingMessages
                .computeIfAbsent(to, key -> new ArrayList<>())
                .add(payload);

        System.out.println("Mensaje pendiente guardado para: " + to);
    }

    private void deliverPendingMessages(String user, WebSocketSession session) {
        List<String> pending = pendingMessages.remove(user);

        if (pending == null) {
            return;
        }

        for (String payload : pending) {
            if (safeSend(session, payload)) {
                sendOrQueueAck(payload);
            }
        }

        System.out.println("Mensajes pendientes entregados a " + user + ": " + pending.size());
    }

    private void deliverPendingAcks(String user, WebSocketSession session) {
        List<String> acks = pendingAcks.remove(user);

        if (acks == null) {
            return;
        }

        for (String ack : acks) {
            safeSend(session, ack);
        }

        System.out.println("ACK pendientes entregados a " + user + ": " + acks.size());
    }

    private void sendOrQueueAck(String payload) {
        String from = ChatPayloadReader.value(payload, "from");
        String messageId = ChatPayloadReader.value(payload, "id");

        if (from == null || from.isBlank() || messageId == null || messageId.isBlank()) {
            return;
        }

        String ack = ChatPayloadReader.ack(messageId);
        WebSocketSession senderSession = users.get(from);

        if (senderSession != null && senderSession.isOpen()) {
            safeSend(senderSession, ack);
            return;
        }

        pendingAcks
                .computeIfAbsent(from, key -> new ArrayList<>())
                .add(ack);

        System.out.println("ACK pendiente guardado para: " + from);
    }

    private boolean safeSend(WebSocketSession session, String payload) {
        try {
            if (session == null || !session.isOpen()) {
                return false;
            }
            session.sendMessage(new TextMessage(payload));
            return true;
        } catch (Exception exception) {
            System.out.println("No se pudo enviar WebSocket: " + exception.getMessage());
            users.entrySet().removeIf(entry -> entry.getValue().getId().equals(session.getId()));
            return false;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        users.entrySet().removeIf(entry -> entry.getValue().getId().equals(session.getId()));
        System.out.println("Usuario desconectado: " + status);
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
}
