package com.example.demo.repository;

import com.example.demo.entity.ChatStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatStateRepository extends JpaRepository<ChatStateEntity, Long> {

    List<ChatStateEntity> findByActiveTrue();
}
