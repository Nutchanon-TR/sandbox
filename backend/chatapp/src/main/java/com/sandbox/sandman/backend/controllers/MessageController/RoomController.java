package com.sandbox.sandman.backend.controllers.MessageController;

import com.sandbox.sandman.backend.model.dto.MessageDto.RoomCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.RoomDto;
import com.sandbox.sandman.backend.services.MessageService.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${app.api.prefix.chat-app}")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping("/room/list/{userId}")
    public ResponseEntity<List<RoomDto>> listRooms(@PathVariable Long userId) {
        return ResponseEntity.ok(roomService.listRoomsForUser(userId));
    }

    @PostMapping("/room/create/{userId}")
    public ResponseEntity<RoomDto> createRoom(
            @PathVariable Long userId,
            @RequestBody RoomCreateRequestDto request) {
        return ResponseEntity.ok(roomService.createRoom(userId, request));
    }
}
