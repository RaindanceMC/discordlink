package de.raindancer.modules.discordlink.hush;

import de.raindancer.core.moderation.review.StaffReviews;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class HushReviewsTest {

    private final UUID ada = UUID.fromString("c9e2c821-7b5e-44e9-a5f7-7554a4da0120");

    private StaffReviews.Review waiting(byte[] preview) {
        return StaffReviews.Review.waiting("mapart", "k3x9q", ada, "Ada", "Map art 2×2 (4 maps)",
                "https://example.com/cat.png", preview, "image/jpeg");
    }

    @Test
    @DisplayName("a waiting review goes out with its picture as base64; later states without one")
    void event() {
        DataObject event = HushEvents.review(5L, waiting(new byte[]{1, 2, 3}));
        assertThat(event.getString("type")).isEqualTo("review");
        assertThat(event.getString("kind")).isEqualTo("mapart");
        assertThat(event.getString("id")).isEqualTo("k3x9q");
        assertThat(event.getString("state")).isEqualTo("waiting");
        assertThat(event.getObject("player").getString("uuid")).isEqualTo(ada.toString());
        assertThat(event.getString("link")).isEqualTo("https://example.com/cat.png");
        assertThat(Base64.getDecoder().decode(event.getString("preview"))).containsExactly(1, 2, 3);
        assertThat(event.getString("previewType")).isEqualTo("image/jpeg");

        DataObject refused = HushEvents.review(6L, waiting(new byte[]{1}).refused("Mo via Discord", "blurry"));
        assertThat(refused.getString("state")).isEqualTo("refused");
        assertThat(refused.getString("by")).isEqualTo("Mo via Discord");
        assertThat(refused.getString("reason")).isEqualTo("blurry");
        assertThat(refused.hasKey("preview")).isFalse();
    }

    @Test
    @DisplayName("a picture over Hush's cap is left out rather than breaking the request")
    void tooBig() {
        DataObject event = HushEvents.review(5L, waiting(new byte[HushEvents.PREVIEW_MAX + 1]));
        assertThat(event.hasKey("preview")).isFalse();
        assertThat(event.toJson().length).isLessThan(HushFeed.MAX_BODY_BYTES);
    }

    @Test
    @DisplayName("reviews go to Hush only with the reverse channel on, so buttons can be answered")
    void wanted() {
        HushToken token = new HushToken("hmc_" + "x".repeat(40));
        assertThat(new HushConfig("u", "s", token, true, true, true, true, true, true, 3, true).wants("review")).isTrue();
        assertThat(new HushConfig("u", "s", token, true, true, true, true, true, false, 3, true).wants("review")).isFalse();
        assertThat(new HushConfig("u", "s", token, true, true, true, true, true, true, 3, false).wants("review")).isFalse();
    }

    @Test
    @DisplayName("the review action reaches Core's decider and answers with its outcome")
    void action() {
        List<String> asked = new ArrayList<>();
        List<DataObject> answers = new ArrayList<>();
        HushActions actions = new HushActions(null, null, null, null,
                (String type, Supplier<DataObject> build) -> answers.add(build.get()), () -> 7L, Runnable::run, null,
                null, null, (kind, id, approve, by, reason) -> {
                    asked.add(kind + " " + id + " " + approve + " " + by + " " + reason);
                    return approve ? "approved" : "already_decided";
                });

        actions.handle(DataObject.empty().put("id", "a1").put("type", "review").put("kind", "mapart")
                .put("ref", "k3x9q").put("decision", "approve").put("by", "Mo"));
        actions.handle(DataObject.empty().put("id", "a2").put("type", "review").put("kind", "mapart")
                .put("ref", "k3x9q").put("decision", "refuse").put("by", "Mo").put("reason", "blurry"));
        actions.handle(DataObject.empty().put("id", "a3").put("type", "review").put("kind", "mapart")
                .put("ref", "k3x9q").put("decision", "maybe"));

        assertThat(asked).containsExactly("mapart k3x9q true Mo ", "mapart k3x9q false Mo blurry");
        assertThat(answers).extracting(a -> a.getString("outcome")).containsExactly("approved", "already_decided", "bad_review");
        assertThat(answers).extracting(a -> a.getBoolean("ok")).containsExactly(true, false, false);
        assertThat(answers.getFirst().getObject("data").getString("ref")).isEqualTo("k3x9q");
    }
}
