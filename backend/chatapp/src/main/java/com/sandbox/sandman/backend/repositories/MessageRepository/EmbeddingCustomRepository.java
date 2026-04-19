package com.sandbox.sandman.backend.repositories.MessageRepository;

import java.util.List;

public interface EmbeddingCustomRepository {
    void saveEmbedding(Long messageId, String vectorStr);
    List<Long> searchSimilarMessages(Long roomId, String vectorStr, int limit);
}
