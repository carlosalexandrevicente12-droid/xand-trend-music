package music.xand.share;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure validation and ranking logic. This class never posts, hides or saves TikTok drafts. */
public final class MusicSelectionPolicy {
    public static final int MIN_AUDIO_SECONDS = 60;

    public enum Decision {
        READY_FOR_OWNER_REVIEW,
        REPLACEMENT_NEEDS_OWNER_REVIEW,
        NEEDS_VERIFICATION,
        NON_ELECTRONIC,
        TOO_SHORT,
        ALREADY_USED
    }

    private static final Set<String> ELECTRONIC_GENRES = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            "eletronica", "musica eletronica", "electronic", "electronic music",
            "electronica", "edm", "electronic dance music",
            "house", "deep house", "bass house", "tech house", "progressive house", "afro house",
            "melodic house", "organic house", "electro house", "future house",
            "techno", "melodic techno", "minimal techno", "hard techno",
            "trance", "psytrance", "progressive trance", "uplifting trance",
            "drum and bass", "dnb", "drum & bass", "liquid dnb",
            "dubstep", "future bass", "hardstyle", "hard dance", "breakbeat",
            "uk garage", "garage house", "electro", "synthwave", "darkwave",
            "downtempo", "ambient electronic", "dance electronica"
        ))
    );

    public static final class Candidate {
        public final String soundId;
        public final String title;
        public final String artist;
        public final String genre;
        public final boolean genreVerified;
        public final Integer trackSeconds;
        public final Integer usablePhotoSeconds;
        public final Long observedViews;
        public final Long observedLikes;
        public final String metricsSource;
        public final String replacesPostId;

        public Candidate(String soundId, String title, String artist, String genre,
                         boolean genreVerified, Integer trackSeconds, Integer usablePhotoSeconds,
                         Long observedViews, Long observedLikes, String metricsSource,
                         String replacesPostId) {
            this.soundId = clean(soundId);
            this.title = clean(title);
            this.artist = clean(artist);
            this.genre = clean(genre);
            this.genreVerified = genreVerified;
            this.trackSeconds = trackSeconds;
            this.usablePhotoSeconds = usablePhotoSeconds;
            this.observedViews = observedViews;
            this.observedLikes = observedLikes;
            this.metricsSource = clean(metricsSource);
            this.replacesPostId = clean(replacesPostId);
        }
    }

    public static final class PublishedSong {
        public final String postId;
        public final String soundId;
        public final String title;
        public final String artist;
        public final Long postViews;

        public PublishedSong(String postId, String soundId, String title, String artist, Long postViews) {
            this.postId = clean(postId);
            this.soundId = clean(soundId);
            this.title = clean(title);
            this.artist = clean(artist);
            this.postViews = postViews;
        }
    }

    private MusicSelectionPolicy() { }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalized(String value) {
        String n = Normalizer.normalize(clean(value), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    public static boolean isConfirmedElectronicGenre(String genre) {
        return ELECTRONIC_GENRES.contains(normalized(genre));
    }

    private static boolean sameSong(Candidate candidate, PublishedSong previous) {
        if (!candidate.soundId.isEmpty() && !previous.soundId.isEmpty()) {
            if (candidate.soundId.equals(previous.soundId)) return true;
        }
        return !candidate.title.isEmpty() && !candidate.artist.isEmpty()
            && normalized(candidate.title).equals(normalized(previous.title))
            && normalized(candidate.artist).equals(normalized(previous.artist));
    }

    /**
     * A replacement view ceiling is supplied externally after the owner settles the limit.
     * A replacement is never an instruction to publish or hide the original post.
     */
    public static Decision validateSafety(Candidate candidate,
                                          Collection<PublishedSong> history,
                                          Long replacementBelowViews) {
        if (candidate == null || candidate.title.isEmpty() || candidate.artist.isEmpty()
            || !candidate.genreVerified || candidate.genre.isEmpty()) {
            return Decision.NEEDS_VERIFICATION;
        }
        if (!isConfirmedElectronicGenre(candidate.genre)) {
            return Decision.NON_ELECTRONIC;
        }
        if (candidate.trackSeconds == null || candidate.usablePhotoSeconds == null) {
            return Decision.NEEDS_VERIFICATION;
        }
        if (candidate.trackSeconds < 0 || candidate.usablePhotoSeconds < 0
            || (candidate.observedViews != null && candidate.observedViews < 0)
            || (candidate.observedLikes != null && candidate.observedLikes < 0)) {
            return Decision.NEEDS_VERIFICATION;
        }
        if (candidate.trackSeconds < MIN_AUDIO_SECONDS
            || candidate.usablePhotoSeconds < MIN_AUDIO_SECONDS) {
            return Decision.TOO_SHORT;
        }
        List<PublishedSong> repetitions = new ArrayList<>();
        if (history != null) {
            for (PublishedSong previous : history) {
                if (previous == null || !sameSong(candidate, previous)) {
                    continue;
                }
                repetitions.add(previous);
            }
        }
        if (repetitions.size() == 1) {
            PublishedSong previous = repetitions.get(0);
            if (replacementBelowViews != null && replacementBelowViews > 0
                && !candidate.replacesPostId.isEmpty()
                && candidate.replacesPostId.equals(previous.postId)
                && previous.postViews != null && previous.postViews >= 0
                && previous.postViews < replacementBelowViews) {
                return Decision.REPLACEMENT_NEEDS_OWNER_REVIEW;
            }
        }
        if (!repetitions.isEmpty()) {
            return Decision.ALREADY_USED;
        }
        return Decision.READY_FOR_OWNER_REVIEW;
    }

    /** Availability of popularity metrics is separate from genre/duration safety. */
    public static Decision evaluate(Candidate candidate, Collection<PublishedSong> history,
                                    Long replacementBelowViews) {
        Decision safety = validateSafety(candidate, history, replacementBelowViews);
        if (safety != Decision.READY_FOR_OWNER_REVIEW) {
            return safety;
        }
        if ((candidate.observedViews == null && candidate.observedLikes == null)
            || candidate.metricsSource.isEmpty()) {
            return Decision.NEEDS_VERIFICATION;
        }
        return safety;
    }

    /**
     * Rank relative to the verified candidate set. A strong result in EITHER views OR likes
     * suffices for high relative priority; no arbitrary "viral" cutoff is invented.
     */
    public static List<Candidate> rankForReview(Collection<Candidate> pool,
                                                 Collection<PublishedSong> history,
                                                 Long replacementBelowViews) {
        List<Candidate> eligible = new ArrayList<>();
        if (pool == null) {
            return eligible;
        }
        for (Candidate c : pool) {
            Decision decision = evaluate(c, history, replacementBelowViews);
            if (decision == Decision.READY_FOR_OWNER_REVIEW
                || decision == Decision.REPLACEMENT_NEEDS_OWNER_REVIEW) {
                eligible.add(c);
            }
        }
        List<Long> views = new ArrayList<>();
        List<Long> likes = new ArrayList<>();
        for (Candidate c : eligible) {
            if (c.observedViews != null) views.add(c.observedViews);
            if (c.observedLikes != null) likes.add(c.observedLikes);
        }
        Collections.sort(views);
        Collections.sort(likes);
        final List<Long> rankedViews = views;
        final List<Long> rankedLikes = likes;
        eligible.sort(new Comparator<Candidate>() {
            @Override public int compare(Candidate a, Candidate b) {
                double aV = percentile(a.observedViews, rankedViews);
                double aL = percentile(a.observedLikes, rankedLikes);
                double bV = percentile(b.observedViews, rankedViews);
                double bL = percentile(b.observedLikes, rankedLikes);
                int primary = Double.compare(Math.max(bV, bL), Math.max(aV, aL));
                if (primary != 0) return primary;
                int secondary = Double.compare(bV + bL, aV + aL);
                if (secondary != 0) return secondary;
                return (a.soundId + a.title + a.artist)
                    .compareTo(b.soundId + b.title + b.artist);
            }
        });
        return eligible;
    }

    private static double percentile(Long value, List<Long> sorted) {
        if (value == null || sorted.isEmpty()) return 0.0;
        int countAtOrBelow = 0;
        for (Long sample : sorted) {
            if (sample <= value) countAtOrBelow++;
        }
        return (double) countAtOrBelow / sorted.size();
    }
}
