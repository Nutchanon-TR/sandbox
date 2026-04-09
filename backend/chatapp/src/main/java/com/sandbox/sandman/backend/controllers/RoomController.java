package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.model.dto.ChatDto.RoomCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.ChatDto.RoomDto;
import com.sandbox.sandman.backend.services.RoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${app.api.prefix.chat-app}")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

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
