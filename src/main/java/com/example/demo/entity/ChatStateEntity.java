package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "chat_state")
public class ChatStateEntity {

    @Id
    private Long chatId;

    private String fromDate;
    private String toDate;

    private boolean active;

    public ChatStateEntity() {}

    public ChatStateEntity(Long chatId, String fromDate, String toDate, boolean active) {
        this.chatId = chatId;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.active = active;
    }

    // getters & setters
    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public String getFromDate() { return fromDate; }
    public void setFromDate(String fromDate) { this.fromDate = fromDate; }

    public String getToDate() { return toDate; }
    public void setToDate(String toDate) { this.toDate = toDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
