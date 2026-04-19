package com.sandbox.sandman.backend.services.SyncHubService.CardService;

import com.sandbox.sandman.backend.model.dto.SyncHubDto.AiDetailDto;
import com.sandbox.sandman.backend.repositories.SyncHubRepository.CardRepository.DetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DetailService {

    private final DetailRepository detailRepository;

    public AiDetailDto getDetail(Long aiId) {
        return detailRepository.findDetailById(aiId)
                .orElseThrow(() -> new RuntimeException("AI not found: " + aiId));
    }
}
