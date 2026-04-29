package com.sandbox.sandman.backend.controllers.SyncHubController.CardController;

import com.sandbox.sandman.backend.model.entity.MessageEntity.Friend;
import com.sandbox.sandman.backend.services.SyncHubService.CardService.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${app.api.prefix.chat-app}")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/ai/{aiId}/friend/{userId}")
    public ResponseEntity<Void> addFriend(@PathVariable Long aiId, @PathVariable Long userId) {
        friendService.addFriend(userId, aiId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/ai/{aiId}/friend/{userId}")
    public ResponseEntity<Void> removeFriend(@PathVariable Long aiId, @PathVariable Long userId) {
        friendService.removeFriend(userId, aiId);
        return ResponseEntity.ok().build();
    }

    //Maybe dont use
    @GetMapping("/user/{userId}/friends")
    public ResponseEntity<List<Friend>> listFriends(@PathVariable Long userId) {
        return ResponseEntity.ok(friendService.listFriends(userId));
    }
}
