package com.sandbox.sandman.backend.controllers.SyncHubController.CardController;

import com.sandbox.sandman.backend.commonauth.CurrentUser;
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
    private final CurrentUser currentUser;

    @PostMapping("/ai/{aiId}/friend")
    public ResponseEntity<Void> addFriend(@PathVariable Long aiId) {
        friendService.addFriend(currentUser.requireUserId(), aiId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/ai/{aiId}/friend")
    public ResponseEntity<Void> removeFriend(@PathVariable Long aiId) {
        friendService.removeFriend(currentUser.requireUserId(), aiId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user/friends")
    public ResponseEntity<List<Friend>> listFriends() {
        return ResponseEntity.ok(friendService.listFriends(currentUser.requireUserId()));
    }
}
