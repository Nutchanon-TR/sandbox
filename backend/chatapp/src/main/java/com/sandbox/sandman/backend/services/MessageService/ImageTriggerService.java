package com.sandbox.sandman.backend.services.MessageService;

import com.sandbox.sandman.backend.model.entity.MessageEntity.AiContext;
import org.springframework.stereotype.Service;

@Service
public class ImageTriggerService {

    public static final String IMAGE_MARKER = "</image>";

    public ImageTriggerResult parseModelReply(String reply, AiContext aiContext) {
        if (reply == null || reply.isBlank()) {
            return new ImageTriggerResult("", ImageTriggerDecision.none());
        }

        String trimmed = reply.trim();
        boolean hasMarker = trimmed.endsWith(IMAGE_MARKER);
        String visibleReply = hasMarker
                ? trimmed.substring(0, trimmed.length() - IMAGE_MARKER.length()).trim()
                : trimmed;

        if (!hasMarker || aiContext == null || !Boolean.TRUE.equals(aiContext.getImageEnabled())) {
            return new ImageTriggerResult(visibleReply, ImageTriggerDecision.none());
        }

        return new ImageTriggerResult(visibleReply, new ImageTriggerDecision(ImageTriggerType.MODEL_MARKER));
    }

    public enum ImageTriggerType {
        NONE,
        MODEL_MARKER
    }

    public record ImageTriggerDecision(ImageTriggerType type) {
        public boolean shouldGenerateImage() {
            return type != ImageTriggerType.NONE;
        }

        public static ImageTriggerDecision none() {
            return new ImageTriggerDecision(ImageTriggerType.NONE);
        }
    }

    public record ImageTriggerResult(String reply, ImageTriggerDecision decision) {}
}
