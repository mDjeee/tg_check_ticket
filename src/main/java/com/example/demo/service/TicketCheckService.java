package com.example.demo.service;

import com.example.demo.service.CsrfService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class TicketCheckService {

    private final RestTemplate restTemplate;
    private final CsrfService csrfService;
    private final ObjectMapper objectMapper = new ObjectMapper();


    public TicketCheckService(RestTemplate restTemplate, CsrfService csrfService) {
        this.restTemplate = restTemplate;
        this.csrfService = csrfService;
    }

    private static final String API_URL = "https://e-ticket.railway.uz/api/v3/trains/availability/space/between/stations";

    private final String tgBotToken = "8526170372:AAFsZL5knqCgRdx78ziYBdc-kQg_JTo2JB4";

    public void runForChat(long chatId, String fromDate, String toDate) throws Exception {
        List<String> dates = generateDateRange(fromDate, toDate);
        checkTicketsForChat(chatId, fromDate);
        checkTicketsForChat(chatId, toDate);
    }

    private List<String> generateDateRange(String fromDate, String toDate) throws Exception {
        List<String> dates = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        LocalDate start = LocalDate.parse(fromDate, formatter);
        LocalDate end = LocalDate.parse(toDate, formatter);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            dates.add(date.format(formatter));
        }
        return dates;
    }


    public void checkTicketsForChat(long chatId, String fromDate) {
        try {
            // Get fresh CSRF token & cookie from Selenium
            String[] csrfData = csrfService.getCsrfTokenAndCookie();
            if (csrfData == null) {
                System.out.println("Failed to get CSRF token");
                return;
            }

            String xsrfToken = csrfData[0];
            String cookie = csrfData[1];

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.set("Accept-Language", "uz");
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Origin", "https://e-ticket.railway.uz");
            headers.set("Referer", "https://e-ticket.railway.uz/uz/pages/trains-page");
            headers.set("X-XSRF-TOKEN", xsrfToken);
            headers.set("device-type", "BROWSER");
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Cookie", cookie);
//            26.12.2025
            String payload = String.format("""
                {
                  "direction":[{"depDate":"%s","fullday":true,"type":"Forward"}],
                  "stationFrom":"2900000",
                  "stationTo":"2900970",
                  "detailNumPlaces":1,
                  "showWithoutPlaces":0
                }
                """, fromDate);

            HttpEntity<String> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(API_URL, HttpMethod.POST, entity, String.class);

            JsonNode body = objectMapper.readTree(response.getBody());
            System.out.println("Response: " + response.getBody());

            boolean ticketsAvailable = false;

// Navigate through JSON
            JsonNode express = body.path("express");
            JsonNode directions = express.path("direction");

            for (JsonNode direction : directions) {
                JsonNode trainsArray = direction.path("trains");
                for (JsonNode trainNode : trainsArray) {
                    JsonNode trainList = trainNode.path("train");
                    for (JsonNode train : trainList) {
                        JsonNode placesArray = train.path("places"); // this is your JSON array
                        List<String> availableTypes = getAvailableSeatTypes(placesArray);

                        if (!availableTypes.isEmpty()) {
                            String trainNumber = train.path("number").asText();
                            String departure = train.path("departure").path("localTime").asText();
                            String date = train.path("departure").path("localDate").asText();

                            String message = "✅ Tickets available for " + date + " (Train " + trainNumber + " at " + departure + "):\n" +
                                    String.join("\n", availableTypes);
                            sendTelegramMessage(message, chatId);
                        } else {
                            System.out.println("No tickets available for train " + train.path("number").asText());
                        }
                    }
                    if (ticketsAvailable) break;
                }
                if (ticketsAvailable) break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendTelegramMessage(String message, long chatId) {
        try {
            String url = String.format("https://api.telegram.org/bot%s/sendMessage", tgBotToken);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String payload = String.format("""
                    {"chat_id":"%s","text":"%s"}
                    """, chatId, message);

            HttpEntity<String> entity = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(url, entity, String.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<String> getAvailableSeatTypes(JsonNode trainNode) {
        List<String> availableTypes = new ArrayList<>();

        System.out.println("Train Node: " + trainNode);
        // The trainNode is the array you posted (train.places / seats info)
        for (JsonNode typeNode : trainNode.path("cars")) {
            String typeName = typeNode.path("type").asText(); // e.g., "Plaskartli", "Kupe", "SV"
            String freeSeats = typeNode.path("freeSeats").asText("0"); // get freeSeats

            if (!freeSeats.equals("0") && !freeSeats.isEmpty()) {
                availableTypes.add(typeName + " (" + freeSeats + " seats)");
            }
        }

        return availableTypes;
    }
}
