package com.sandbox.sandman.backend.model.entity.ChatEntity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_context", schema = "chat")
@Data
@NoArgsConstructor
public class AiContext {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false, unique = true)
    private Room room;

    @Column(name = "ai_name", nullable = false, length = 100)
    private String aiName = "AI Assistant";

    @Column(name = "system_text", nullable = false, columnDefinition = "TEXT")
    private String systemText;
}
