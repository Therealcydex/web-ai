package com.stayora.reviews;

import static org.assertj.core.api.Assertions.assertThat;

import com.stayora.ai.AiPrivacy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReviewThemesTest {
  private static Map<String, String> themes(String body, String... tags) {
    int[] states = ReviewThemes.classify(body, List.of(tags));
    var result = new LinkedHashMap<String, String>();
    for (int i = 0; i < states.length; i++)
      if (states[i] != 0)
        result.put(
            ReviewThemes.THEMES.get(i).id(),
            switch (states[i]) {
              case 1 -> "+";
              case 2 -> "-";
              default -> "+-";
            });
    return result;
  }

  @Test
  void readsTheSeededDemoReviewsSensibly() {
    assertThat(
            themes(
                "The courtyard was spotless and the bedrooms felt calm after a busy day. Our host shared clear arrival instructions and made us feel welcome."))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "cleanliness", "+",
                "comfort", "+",
                "check-in", "+",
                "communication", "+",
                "hospitality", "+"));
    assertThat(
            themes(
                "Good value for a short break. The living area was pleasant, though the kitchen needed a more thorough clean before we arrived."))
        .containsExactlyInAnyOrderEntriesOf(Map.of("value", "+", "cleanliness", "-"));
    assertThat(
            themes(
                "The space was comfortable, but we waited for arrival instructions and the check-in felt rushed. A clearer welcome message would help."))
        .containsEntry("comfort", "+")
        .containsEntry("check-in", "-")
        .containsEntry("communication", "-")
        .containsEntry("hospitality", "-");
  }

  @Test
  void negationAndMixedClausesAreCountedOnBothSides() {
    assertThat(themes("The flat was not very clean.")).containsEntry("cleanliness", "-");
    assertThat(themes("It was never quiet at night.")).containsEntry("comfort", "-");
    assertThat(themes("Not as described at all.")).containsEntry("accuracy", "-");
    assertThat(themes("The kitchen was clean. The bathroom was dirty."))
        .containsEntry("cleanliness", "+-");
    assertThat(themes("A kind of loft with a message board.")).isEmpty();
  }

  @Test
  void explicitTagsOverrideTextForTheirCategoryOnly() {
    assertThat(themes("Spotless and comfortable.", "needs-cleaning"))
        .containsEntry("cleanliness", "-")
        .containsEntry("comfort", "+");
    assertThat(themes("Short note.", "easy-check-in", "slow-replies"))
        .containsExactlyInAnyOrderEntriesOf(Map.of("check-in", "+", "communication", "-"));
    assertThat(themes(null)).isEmpty();
  }

  @Test
  void onlyThemeCountsSurviveTheSharedPrivacyFilter() {
    String safe =
        new AiPrivacy()
            .context(
                "reviews count=4; rating average=4.25; cleanliness positive=3; check-in negative=1; Sami said the room smelled; secret theme=9");
    assertThat(safe)
        .contains(
            "reviews count=4",
            "rating average=4.25",
            "cleanliness positive=3",
            "check-in negative=1")
        .doesNotContain("Sami", "smelled", "secret theme");
  }
}
