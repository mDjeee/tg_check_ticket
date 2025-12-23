package com.example.demo.service;

import com.example.demo.entity.ChatStateEntity;
import com.example.demo.repository.ChatStateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TelegramBotService {

    private final RestTemplate restTemplate;
    private final TicketCheckService ticketCheckService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final String tgBotToken = "8526170372:AAFsZL5knqCgRdx78ziYBdc-kQg_JTo2JB4";
    private long lastUpdateId = 0;

    private final ChatStateRepository chatStateRepository;

    private static class ChatState {
        String fromDate;
        String toDate;
        boolean active;

        ChatState(String fromDate, String toDate) {
            this.fromDate = fromDate;
            this.toDate = toDate;
            this.active = true;
        }
    }

    public TelegramBotService(
            RestTemplate restTemplate,
            TicketCheckService ticketCheckService,
            ChatStateRepository chatStateRepository
            ) {
        this.restTemplate = restTemplate;
        this.ticketCheckService = ticketCheckService;
        this.chatStateRepository = chatStateRepository;
    }

    @Scheduled(fixedDelay = 2000)
    public void pollUpdates() {
        try {
            String url = String.format("https://api.telegram.org/bot%s/getUpdates?timeout=100&offset=%d", tgBotToken, lastUpdateId + 1);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode body = objectMapper.readTree(response.getBody());

            if (body.path("ok").asBoolean()) {
                for (JsonNode update : body.path("result")) {
                    lastUpdateId = update.path("update_id").asLong();

                    if (update.has("message")) {
                        JsonNode message = update.path("message");
                        long chatId = message.path("chat").path("id").asLong();
                        String text = message.path("text").asText();

                        handleMessage(chatId, text);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleMessage(long chatId, String text) {
        try {
            if (text.startsWith("/set_dates")) {
                String[] parts = text.split(" ");
                if (parts.length == 3) {
                    String fromDate = parts[1];
                    String toDate = parts[2];

                    ChatStateEntity state = new ChatStateEntity(
                            chatId, fromDate, toDate, true
                    );
                    chatStateRepository.save(state);

                    sendMessage(chatId, "✅ Dates set! Checking tickets from " + fromDate + " to " + toDate);

                    // Run asynchronously to avoid blocking
                    executor.submit(() -> {
                        try {
                            ticketCheckService.runForChat(chatId, fromDate, toDate);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

                } else {
                    sendMessage(chatId, "⚠️ Usage: /set_dates 26.12.2025 28.12.2025");
                }

            } else if (text.startsWith("/update_dates")) {
                String[] parts = text.split(" ");
                if (parts.length == 3) {
                    String fromDate = parts[1];
                    String toDate = parts[2];

                    chatStateRepository.findById(chatId).ifPresentOrElse(state -> {
                        state.setFromDate(fromDate);
                        state.setToDate(toDate);
                        state.setActive(true);
                        chatStateRepository.save(state);

                        sendMessage(chatId, "✅ Dates updated!");
                        executor.submit(() -> {
                            try {
                                ticketCheckService.runForChat(chatId, fromDate, toDate);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }, () -> sendMessage(chatId, "⚠️ Use /set_dates first"));
                } else {
                    sendMessage(chatId, "⚠️ Usage: /update_dates 26.12.2025 28.12.2025");
                }

            } else if (text.equals("/stop")) {
                chatStateRepository.findById(chatId).ifPresentOrElse(state -> {
                    state.setActive(false);
                    chatStateRepository.save(state);
                    sendMessage(chatId, "🛑 Ticket checking stopped.");
                }, () -> sendMessage(chatId, "⚠️ Nothing to stop."));
            } else {
                sendMessage(chatId, "🤖 I can check train tickets. Use: /set_dates <from> <to>");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Scheduled(fixedRate = 60000)
    public void autoCheckTickets() {
        chatStateRepository.findByActiveTrue().forEach(state ->
                executor.submit(() ->
                        {
                            try {
                                ticketCheckService.runForChat(
                                        state.getChatId(),
                                        state.getFromDate(),
                                        state.getToDate()
                                );
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                )
        );
    }

    public void sendMessage(long chatId, String message) {
        try {
            String url = String.format("https://api.telegram.org/bot%s/sendMessage", tgBotToken);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Escape quotes to avoid breaking JSON
            String safeMessage = message.replace("\"", "\\\"");

            String payload = String.format("""
                    {"chat_id": "%d", "text": "%s"}
                    """, chatId, safeMessage);

            restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), String.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
