package com.sandbox.sandman.backend.services.SyncHubService;

import com.sandbox.sandman.backend.model.dto.SyncHubDto.AiContextUserDto;
import com.sandbox.sandman.backend.repositories.SyncHubRepository.CardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;

    public List<AiContextUserDto> getAiContextsWithUsers() {
        return cardRepository.findAiContextsWithUsers();
    }
}
