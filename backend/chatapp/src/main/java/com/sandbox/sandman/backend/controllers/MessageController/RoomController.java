package com.sandbox.sandman.backend.controllers.MessageController;

import com.sandbox.sandman.backend.commonauth.CurrentUser;
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
    private final CurrentUser currentUser;

    @GetMapping("/room/list")
    public ResponseEntity<List<RoomDto>> listRooms() {
        return ResponseEntity.ok(roomService.listRoomsForUser(currentUser.requireUserId()));
    }

    @PostMapping("/room/create")
    public ResponseEntity<RoomDto> createRoom(@RequestBody RoomCreateRequestDto request) {
        return ResponseEntity.ok(roomService.createRoom(currentUser.requireUserId(), request));
    }
}
