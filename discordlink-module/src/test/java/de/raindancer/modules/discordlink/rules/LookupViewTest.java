package de.raindancer.modules.discordlink.rules;

import de.raindancer.modules.discordlink.model.LookupReport;
import de.raindancer.modules.discordlink.model.PlayerCard;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LookupViewTest {

    static final String JSON = """
            {"query":"cherryb_","title":"CHERRYB_","url":"https://www.twitch.tv/cherryb_",
             "thumbnail":"https://static-cdn.jtvnw.net/x.png","panel":"https://applications.lillyyxoxo.net/admin/lookup?q=cherryb_",
             "sections":[
               {"name":"Twitch","rows":[
                 {"state":"info","text":"Account since 4 May 2019","detail":"Affiliate · 7 years"},
                 {"state":"yes","text":"Follows since 1 Apr 2025","detail":"for 1 year, 6 months"},
                 {"state":"sub","text":"Subscribed, Tier 1","detail":"Gifted by Generous_Gifter"},
                 {"state":"unknown","text":"Ban status unknown","detail":"Needs Lilly's channel connected"},
                 {"state":"no","text":"Doesn't follow","detail":null}]},
               {"name":"Applications","rows":[{"state":"info","text":"None under these names","detail":null}]}],
             "misses":["Minecraft","Lilly's Discord"]}""";

    private final LookupView view = new LookupView();

    @Test
    void theWebsiteAnswerIsRead() {
        LookupReport report = LookupReport.parse(JSON).orElseThrow();
        assertThat(report.title()).isEqualTo("CHERRYB_");
        assertThat(report.sections()).extracting(LookupReport.Section::name).containsExactly("Twitch", "Applications");
        assertThat(report.sections().getFirst().rows().get(2).detail()).isEqualTo("Gifted by Generous_Gifter");
        assertThat(report.sections().getFirst().rows().get(4).detail()).isNull();
        assertThat(report.misses()).containsExactly("Minecraft", "Lilly's Discord");
        assertThat(LookupReport.parse("not json")).isEmpty();
        assertThat(LookupReport.parse("{\"error\":\"unauthorized\"}")).isEmpty();
    }

    @Test
    void anEmbedWithOneFieldPerSectionAndAMarkPerRow() {
        MessageEmbed embed = view.embed(LookupReport.parse(JSON).orElseThrow(), Optional.empty(), Optional.empty());

        assertThat(embed.getTitle()).isEqualTo("CHERRYB_");
        assertThat(embed.getUrl()).isEqualTo("https://www.twitch.tv/cherryb_");
        assertThat(embed.getThumbnail().getUrl()).isEqualTo("https://static-cdn.jtvnw.net/x.png");
        assertThat(embed.getFields()).extracting(MessageEmbed.Field::getName).containsExactly("Twitch", "Applications");
        String twitch = embed.getFields().getFirst().getValue();
        assertThat(twitch).contains("✅ **Follows since 1 Apr 2025** · for 1 year, 6 months")
                .contains("⭐ **Subscribed, Tier 1** · Gifted by Generous_Gifter")
                .contains("❔ **Ban status unknown** · Needs Lilly's channel connected")
                .contains("❌ **Doesn't follow**").contains("▫️ **Account since 4 May 2019**");
        assertThat(embed.getDescription()).contains("Nothing found on Minecraft · Lilly's Discord")
                .contains("[Open in the panel](https://applications.lillyyxoxo.net/admin/lookup?q=cherryb_)");
    }

    @Test
    void theServersOwnStatsComeFirstWithWhoLinkedIt() {
        PlayerCard card = new PlayerCard(UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"), "Lilly_Fan", Optional.empty(),
                Optional.of(1_760_000_000_000L), Optional.of("smp"), Optional.of(1_750_000_000_000L), 754, Optional.of("default"), true);
        MessageEmbed embed = view.embed(LookupReport.parse(JSON).orElseThrow(), Optional.of(card), Optional.of("700000000000000001"));

        MessageEmbed.Field server = embed.getFields().getFirst();
        assertThat(server.getName()).isEqualTo("On the Minecraft server");
        assertThat(server.getValue()).contains("**Lilly_Fan**").contains("Last seen <t:1760000000:R> on smp")
                .contains("First joined <t:1750000000:D>").contains("12 h 34 min played").contains("Rank default")
                .contains("Linked to <@700000000000000001>");
    }

    @Test
    void onlyTheServerKnowsThemWhenTheWebsiteIsDown() {
        PlayerCard card = new PlayerCard(UUID.randomUUID(), "Steve", Optional.of("arena"), Optional.empty(), Optional.empty(),
                Optional.empty(), 5, Optional.empty(), false);
        MessageEmbed embed = view.embed(null, Optional.of(card), Optional.empty());
        assertThat(embed.getTitle()).isEqualTo("Steve");
        assertThat(embed.getFields().getFirst().getValue()).contains("Online on arena").contains("Not linked to Discord");
        assertThat(embed.getDescription()).contains("The website couldn't be asked");
    }

    @Test
    void longSectionsAreCutToWhatDiscordTakes() {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            rows.append(i == 0 ? "" : ",").append("{\"state\":\"info\",\"text\":\"Row number ").append(i)
                    .append(" with a long text to fill the field\",\"detail\":\"and some detail\"}");
        }
        String json = "{\"query\":\"x\",\"title\":\"x\",\"sections\":[{\"name\":\"Twitch\",\"rows\":[" + rows + "]}],\"misses\":[]}";
        MessageEmbed embed = view.embed(LookupReport.parse(json).orElseThrow(), Optional.empty(), Optional.empty());
        assertThat(embed.getFields().getFirst().getValue().length()).isLessThanOrEqualTo(MessageEmbed.VALUE_MAX_LENGTH);
        assertThat(embed.getLength()).isLessThanOrEqualTo(MessageEmbed.EMBED_MAX_LENGTH_BOT);
        assertThat(List.of(embed.getFields().getFirst().getValue())).allMatch(v -> v.endsWith("…"));
    }
}
