package com.sandbox.sandman.backend.services.MessageService;

import com.sandbox.sandman.backend.model.entity.MessageEntity.AiContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTriggerServiceTest {

    private final ImageTriggerService service = new ImageTriggerService();

    @Test
    void parseModelReplyTriggersWhenMarkerIsExactSuffix() {
        AiContext aiContext = new AiContext();
        aiContext.setImageEnabled(true);

        ImageTriggerService.ImageTriggerResult result =
                service.parseModelReply("Sure, I took this for you.</image>", aiContext);

        assertThat(result.reply()).isEqualTo("Sure, I took this for you.");
        assertThat(result.decision().shouldGenerateImage()).isTrue();
        assertThat(result.decision().type()).isEqualTo(ImageTriggerService.ImageTriggerType.MODEL_MARKER);
    }

    @Test
    void parseModelReplyStripsMarkerButDoesNotTriggerWhenImagesAreDisabled() {
        AiContext aiContext = new AiContext();
        aiContext.setImageEnabled(false);

        ImageTriggerService.ImageTriggerResult result =
                service.parseModelReply("Here you go.</image>", aiContext);

        assertThat(result.reply()).isEqualTo("Here you go.");
        assertThat(result.decision().shouldGenerateImage()).isFalse();
    }

    @Test
    void parseModelReplyDoesNotTriggerForInlineMarker() {
        AiContext aiContext = new AiContext();
        aiContext.setImageEnabled(true);

        ImageTriggerService.ImageTriggerResult result =
                service.parseModelReply("Do not print </image> in the middle of a reply.", aiContext);

        assertThat(result.reply()).isEqualTo("Do not print </image> in the middle of a reply.");
        assertThat(result.decision().shouldGenerateImage()).isFalse();
    }
}
