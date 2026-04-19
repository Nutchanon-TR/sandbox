package com.sandbox.sandman.backend.services.SyncHubService.BlogService;

import com.sandbox.sandman.backend.model.dto.SyncHubDto.AiContextUserDto;
import com.sandbox.sandman.backend.repositories.SyncHubRepository.BlogRepository.ListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListService {

    private final ListRepository listRepository;

    public List<AiContextUserDto> getAiContextsWithUsers() {
        return listRepository.findAiContextsWithUsers();
    }
}
