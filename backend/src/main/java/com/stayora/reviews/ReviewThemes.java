package com.stayora.reviews;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Counts recurring topics in review text with fixed keyword lists. Runs on the server so review
 * bodies never reach the AI provider; only the resulting counts do. Explicit host tags override the
 * text for their category. Results are approximate keyword matches, not semantic analysis.
 */
public final class ReviewThemes {
  public static final String POSITIVE = "positive";
  public static final String NEGATIVE = "negative";
  private static final int POS = 1;
  private static final int NEG = 2;

  private static final Pattern CLAUSE =
      Pattern.compile("(?i)[.!?;\\n]+|\\b(?:but|though|although|however|yet)\\b");
  private static final Pattern SUGGESTION =
      Pattern.compile(
          "(?i)\\b(?:would help|would be nice|would have liked|could be better|could improve|should|wish)\\b");
  private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();
  private static final String NEGATION =
      "(?<![a-z])(?:not|never|no|hardly|wasn't|weren't|isn't|didn't)\\s+(?:[a-z']+\\s+){0,2}";

  static final List<Theme> THEMES =
      List.of(
          new Theme(
              "cleanliness",
              "Cleanliness",
              "cleanliness",
              List.of(
                  "clean",
                  "spotless",
                  "tidy",
                  "fresh linens",
                  "well-kept",
                  "well kept",
                  "immaculate"),
              List.of(
                  "dirty",
                  "dusty",
                  "untidy",
                  "messy",
                  "stains",
                  "stained",
                  "smelly",
                  "mould",
                  "mold",
                  "thorough clean",
                  "more cleaning",
                  "needs cleaning",
                  "needed cleaning")),
          new Theme(
              "hospitality",
              "Host welcome",
              "hospitality",
              List.of(
                  "welcome",
                  "welcoming",
                  "welcomed",
                  "friendly",
                  "hospitable",
                  "helpful",
                  "thoughtful"),
              List.of("unwelcoming", "rude", "unfriendly", "unhelpful", "impolite")),
          new Theme(
              "communication",
              "Communication",
              "communication",
              List.of(
                  "answered",
                  "responsive",
                  "quick replies",
                  "replied quickly",
                  "communication was friendly",
                  "communication was good",
                  "communication was great",
                  "communication was easy",
                  "good communication",
                  "great communication",
                  "clear instructions",
                  "clear arrival instructions",
                  "kept us informed"),
              List.of(
                  "slow replies",
                  "slow to reply",
                  "no reply",
                  "unresponsive",
                  "waited for",
                  "hard to reach",
                  "poor communication",
                  "communication was poor",
                  "communication was slow")),
          new Theme(
              "check-in",
              "Check-in",
              "check-in",
              List.of(
                  "easy check-in",
                  "smooth check-in",
                  "self check-in",
                  "arrival instructions",
                  "easy arrival",
                  "smooth arrival"),
              List.of("difficult check-in", "rushed", "late check-in", "locked out")),
          new Theme(
              "accuracy",
              "As described",
              "accuracy",
              List.of(
                  "as described",
                  "as advertised",
                  "matched the photos",
                  "fair idea",
                  "true to the photos"),
              List.of("not as described", "misleading", "different from the photos")),
          new Theme(
              "comfort",
              "Comfort",
              null,
              List.of(
                  "comfortable",
                  "comfy",
                  "cosy",
                  "cozy",
                  "calm",
                  "relaxing",
                  "peaceful",
                  "quiet",
                  "spacious"),
              List.of("uncomfortable", "noisy", "loud", "cramped")),
          new Theme(
              "location",
              "Location",
              null,
              List.of(
                  "great location",
                  "good location",
                  "central",
                  "close to",
                  "walking distance",
                  "well located",
                  "convenient"),
              List.of("far from", "hard to find", "remote", "bad location", "isolated")),
          new Theme(
              "value",
              "Value",
              null,
              List.of("good value", "great value", "worth", "affordable", "value for money"),
              List.of("overpriced", "expensive", "poor value")));

  private ReviewThemes() {}

  /** Theme counts across reviews; a review mentioning both sides of a theme counts in both. */
  static List<ReviewService.ThemeCount> count(List<Review> reviews) {
    var result = new ArrayList<ReviewService.ThemeCount>();
    for (int i = 0; i < THEMES.size(); i++) {
      long positive = 0;
      long negative = 0;
      for (Review review : reviews) {
        int state = classify(review.body, tagIds(review))[i];
        if ((state & POS) != 0) positive++;
        if ((state & NEG) != 0) negative++;
      }
      if (positive + negative > 0) {
        Theme theme = THEMES.get(i);
        result.add(new ReviewService.ThemeCount(theme.id(), theme.label(), positive, negative));
      }
    }
    result.sort(
        Comparator.comparingLong((ReviewService.ThemeCount t) -> t.positive() + t.negative())
            .reversed());
    return result;
  }

  /** Per-theme bit flags (1 positive, 2 negative) for one review, in {@link #THEMES} order. */
  public static int[] classify(String body, List<String> tags) {
    int[] states = new int[THEMES.size()];
    for (String clause : CLAUSE.split(body == null ? "" : body.toLowerCase(Locale.ROOT))) {
      boolean suggestion = SUGGESTION.matcher(clause).find();
      for (int i = 0; i < THEMES.size(); i++) {
        Theme theme = THEMES.get(i);
        boolean negative = theme.negative().stream().anyMatch(k -> word(k).matcher(clause).find());
        negative |= theme.positive().stream().anyMatch(k -> negated(k).matcher(clause).find());
        boolean positive =
            !negative && theme.positive().stream().anyMatch(k -> word(k).matcher(clause).find());
        if (suggestion && positive) {
          positive = false;
          negative = true;
        }
        if (positive) states[i] |= POS;
        if (negative) states[i] |= NEG;
      }
    }
    for (String id : tags) {
      ReviewService.TAGS.stream()
          .filter(t -> t.id().equals(id))
          .findFirst()
          .ifPresent(
              tag -> {
                for (int i = 0; i < THEMES.size(); i++)
                  if (tag.category().equals(THEMES.get(i).tagCategory()))
                    states[i] = "GOOD".equals(tag.sentiment()) ? POS : NEG;
              });
    }
    return states;
  }

  private static Pattern word(String keyword) {
    return PATTERNS.computeIfAbsent(
        keyword, k -> Pattern.compile("(?<![a-z])" + Pattern.quote(k) + "(?![a-z])"));
  }

  private static Pattern negated(String keyword) {
    String plain = word(keyword).pattern();
    return PATTERNS.computeIfAbsent("!" + keyword, k -> Pattern.compile(NEGATION + plain));
  }

  private static List<String> tagIds(Review review) {
    return review.tags == null || review.tags.isEmpty()
        ? List.of()
        : List.of(review.tags.split(","));
  }

  record Theme(
      String id, String label, String tagCategory, List<String> positive, List<String> negative) {}
}
