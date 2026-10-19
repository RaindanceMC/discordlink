package de.raindancer.modules.discordlink.model;

import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the applications website's /api/lookup answers: the Look up page's search as rows ready to show, in the
 * website's words. The bot only lays them out.
 */
public record LookupReport(String query, String title, String url, String thumbnail, String panel,
                           List<Section> sections, List<String> misses) {

    /** {@code state}: yes, no, unknown, sub or info. */
    public record Row(String state, String text, String detail) {
    }

    public record Section(String name, List<Row> rows) {
    }

    /** Empty for anything that isn't a report (an error answer, broken JSON). */
    public static Optional<LookupReport> parse(String json) {
        try {
            DataObject o = DataObject.fromJson(json);
            if (!o.hasKey("sections")) {
                return Optional.empty();
            }
            List<Section> sections = new ArrayList<>();
            DataArray all = o.getArray("sections");
            for (int i = 0; i < all.length(); i++) {
                DataObject s = all.getObject(i);
                List<Row> rows = new ArrayList<>();
                DataArray rs = s.getArray("rows");
                for (int j = 0; j < rs.length(); j++) {
                    DataObject r = rs.getObject(j);
                    rows.add(new Row(r.getString("state", "info"), r.getString("text", ""), r.getString("detail", null)));
                }
                sections.add(new Section(s.getString("name", ""), List.copyOf(rows)));
            }
            List<String> misses = new ArrayList<>();
            if (o.hasKey("misses") && !o.isNull("misses")) {
                DataArray m = o.getArray("misses");
                for (int i = 0; i < m.length(); i++) {
                    misses.add(m.getString(i));
                }
            }
            return Optional.of(new LookupReport(o.getString("query", ""), o.getString("title", o.getString("query", "")),
                    o.getString("url", null), o.getString("thumbnail", null), o.getString("panel", null),
                    List.copyOf(sections), List.copyOf(misses)));
        } catch (RuntimeException notAReport) {
            return Optional.empty();
        }
    }
}
