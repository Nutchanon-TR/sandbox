package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.model.dto.FriendshipDto;
import com.sandbox.sandman.backend.model.dto.UserSummaryDto;
import com.sandbox.sandman.backend.commonauth.CurrentUser;
import com.sandbox.sandman.backend.services.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${app.api.prefix.b-post}")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;
    private final CurrentUser currentUser;

    @PostMapping("/friends/requests")
    public ResponseEntity<FriendshipDto> request(@RequestBody Map<String, Long> body) {
        Long addresseeId = body.get("addresseeId");
        return ResponseEntity.ok(friendService.sendRequest(currentUser.requireUserId(), addresseeId));
    }

    @PostMapping("/friends/requests/{id}/accept")
    public ResponseEntity<FriendshipDto> accept(@PathVariable Long id) {
        return ResponseEntity.ok(friendService.respond(currentUser.requireUserId(), id, true));
    }

    @PostMapping("/friends/requests/{id}/decline")
    public ResponseEntity<FriendshipDto> decline(@PathVariable Long id) {
        return ResponseEntity.ok(friendService.respond(currentUser.requireUserId(), id, false));
    }

    @GetMapping("/friends")
    public ResponseEntity<List<UserSummaryDto>> friends() {
        return ResponseEntity.ok(friendService.listFriends(currentUser.requireUserId()));
    }

    @GetMapping("/friends/requests/incoming")
    public ResponseEntity<List<FriendshipDto>> incoming() {
        return ResponseEntity.ok(friendService.incomingPending(currentUser.requireUserId()));
    }

    @GetMapping("/friends/requests/outgoing")
    public ResponseEntity<List<FriendshipDto>> outgoing() {
        return ResponseEntity.ok(friendService.outgoingPending(currentUser.requireUserId()));
    }

    @GetMapping("/users/search")
    public ResponseEntity<List<UserSummaryDto>> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(friendService.searchUsers(q, currentUser.requireUserId()));
    }
}
