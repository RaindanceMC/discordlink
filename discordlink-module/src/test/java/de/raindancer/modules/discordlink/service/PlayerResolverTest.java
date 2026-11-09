package de.raindancer.modules.discordlink.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerResolverTest {

    private static final UUID YEUK = UUID.fromString("8e8369df-3a97-450c-9d26-39cbf3a84a7f");
    private static final PlayerResolver.Known NOBODY = new PlayerResolver.Known() {
        @Override
        public Optional<PlayerResolver.Resolved> byName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<String> nameOf(UUID id) {
            return Optional.empty();
        }
    };

    @Test
    @DisplayName("Core's lookup is asked first; when it has nothing, Mojang is asked, by name or by uuid")
    void coreFirst() {
        PlayerResolver viaCore = new PlayerResolver(NOBODY, url -> Optional.empty(),
                typed -> Optional.of(new PlayerResolver.Resolved(YEUK, "Yeukoula")));
        assertThat(viaCore.resolve("yeukoula")).contains(new PlayerResolver.Resolved(YEUK, "Yeukoula"));

        PlayerResolver mojangOnly = new PlayerResolver(NOBODY, url -> url.endsWith("/Yeukoula")
                ? Optional.of("{\"name\":\"Yeukoula\",\"id\":\"8e8369df3a97450c9d2639cbf3a84a7f\"}") : Optional.empty());
        assertThat(mojangOnly.resolve("Yeukoula")).contains(new PlayerResolver.Resolved(YEUK, "Yeukoula"));
        assertThat(mojangOnly.resolve("Nobody")).isEmpty();
        assertThat(mojangOnly.resolve("not a name!")).isEmpty();
        assertThat(mojangOnly.resolve("")).isEmpty();
        assertThat(mojangOnly.resolve(YEUK.toString()).orElseThrow().id()).isEqualTo(YEUK);
    }

    @Test
    @DisplayName("a uuid whose name nobody knows still resolves, with its first eight characters as the name")
    void uuidWithoutName() {
        assertThat(new PlayerResolver(NOBODY, url -> Optional.empty()).resolve("8e8369df3a97450c9d2639cbf3a84a7f"))
                .contains(new PlayerResolver.Resolved(YEUK, "8e8369df"));
    }
}
