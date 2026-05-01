package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.UserSummaryDto;
import com.sandbox.sandman.backend.commonauth.AuthUser;
import com.sandbox.sandman.backend.commonauth.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final AuthUserRepository userRepository;

    public UserSummaryDto toDto(AuthUser user) {
        if (user == null) return null;
        return new UserSummaryDto(
                user.getId(),
                user.getSupabaseUid() == null ? null : user.getSupabaseUid().toString(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getLastSeenAt());
    }

    public Map<Long, UserSummaryDto> loadAll(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(AuthUser::getId, this::toDto, (a, b) -> a));
    }

    public UserSummaryDto loadOne(Long id) {
        if (id == null) return null;
        return userRepository.findById(id).map(this::toDto).orElse(null);
    }
}
