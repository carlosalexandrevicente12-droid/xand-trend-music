package music.xand.share;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MusicSelectionPolicyTest {
    private static MusicSelectionPolicy.Candidate candidate(String id, String genre,
            boolean verified, int seconds, int usableSeconds, Long views, Long likes,
            String replaces) {
        return new MusicSelectionPolicy.Candidate(id, "Faixa", "Artista", genre, verified,
            seconds, usableSeconds, views, likes, "TikTok (data e link verificados)", replaces);
    }

    private static void assertDecision(MusicSelectionPolicy.Decision expected,
                                       MusicSelectionPolicy.Candidate c,
                                       List<MusicSelectionPolicy.PublishedSong> history,
                                       Long ceiling) {
        MusicSelectionPolicy.Decision actual = MusicSelectionPolicy.evaluate(c, history, ceiling);
        if (actual != expected) throw new AssertionError(expected + " != " + actual);
    }

    public static void main(String[] args) {
        MusicSelectionPolicy.Candidate good = candidate("sound-1", "Melodic Techno", true,
            200, 60, 120000L, 90L, "");
        assertDecision(MusicSelectionPolicy.Decision.READY_FOR_OWNER_REVIEW,
            good, Collections.<MusicSelectionPolicy.PublishedSong>emptyList(), null);
        assertDecision(MusicSelectionPolicy.Decision.NON_ELECTRONIC,
            candidate("pop-1", "pop", true, 180, 60, 999999L, 10000L, ""),
            Collections.<MusicSelectionPolicy.PublishedSong>emptyList(), null);
        assertDecision(MusicSelectionPolicy.Decision.NEEDS_VERIFICATION,
            candidate("unknown", "EDM", false, 180, 60, 20L, 5L, ""),
            Collections.<MusicSelectionPolicy.PublishedSong>emptyList(), null);
        assertDecision(MusicSelectionPolicy.Decision.TOO_SHORT,
            candidate("short", "house", true, 59, 59, 1000L, 10L, ""),
            Collections.<MusicSelectionPolicy.PublishedSong>emptyList(), null);
        assertDecision(MusicSelectionPolicy.Decision.TOO_SHORT,
            candidate("photo-short", "house", true, 200, 59, 1000L, 10L, ""),
            Collections.<MusicSelectionPolicy.PublishedSong>emptyList(), null);
        List<MusicSelectionPolicy.PublishedSong> history = Collections.singletonList(
            new MusicSelectionPolicy.PublishedSong("old-post", "sound-1", "Faixa", "Artista", 420L));
        assertDecision(MusicSelectionPolicy.Decision.ALREADY_USED, good, history, 500L);
        assertDecision(MusicSelectionPolicy.Decision.REPLACEMENT_NEEDS_OWNER_REVIEW,
            candidate("sound-1", "techno", true, 200, 60, 120000L, 90L, "old-post"),
            history, 500L);
        assertDecision(MusicSelectionPolicy.Decision.ALREADY_USED,
            candidate("sound-1", "techno", true, 200, 60, 120000L, 90L, "old-post"),
            history, 400L);
        assertDecision(MusicSelectionPolicy.Decision.ALREADY_USED,
            candidate("different-id", "house", true, 200, 60, 120000L, 90L, ""),
            history, 500L);
        List<MusicSelectionPolicy.PublishedSong> twice = Arrays.asList(history.get(0),
            new MusicSelectionPolicy.PublishedSong("other-post", "other-sound", "Faixa",
                "Artista", 100L));
        assertDecision(MusicSelectionPolicy.Decision.ALREADY_USED,
            candidate("sound-1", "house", true, 200, 60, 1000L, 300L, "old-post"),
            twice, 500L);
        assertDecision(MusicSelectionPolicy.Decision.NEEDS_VERIFICATION,
            candidate("invalid", "house", true, -1, 60, 1000L, 300L, ""),
            Collections.<MusicSelectionPolicy.PublishedSong>emptyList(), null);
        if (!MusicSelectionPolicy.isConfirmedElectronicGenre("Música eletrônica")) {
            throw new AssertionError("Falha ao reconhecer rótulo eletrônico confirmado");
        }
        MusicSelectionPolicy.Candidate noMetrics = new MusicSelectionPolicy.Candidate(
            "sound-2", "Faixa", "Artista", "EDM", true, 180, 60,
            null, null, "", "");
        assertDecision(MusicSelectionPolicy.Decision.NEEDS_VERIFICATION, noMetrics,
            Collections.<MusicSelectionPolicy.PublishedSong>emptyList(), null);
        if (MusicSelectionPolicy.validateSafety(noMetrics,
            Collections.<MusicSelectionPolicy.PublishedSong>emptyList(), null)
            != MusicSelectionPolicy.Decision.READY_FOR_OWNER_REVIEW) {
            throw new AssertionError("Métricas ausentes não podem impedir revisão manual segura");
        }
        MusicSelectionPolicy.Candidate manyLikes = candidate("sound-3", "house", true,
            180, 60, 100L, 200000L, "");
        MusicSelectionPolicy.Candidate manyViews = candidate("sound-4", "trance", true,
            180, 60, 200000L, 10L, "");
        MusicSelectionPolicy.Candidate weak = candidate("sound-5", "techno", true,
            180, 60, 100L, 10L, "");
        List<MusicSelectionPolicy.Candidate> ranked = MusicSelectionPolicy.rankForReview(
            Arrays.asList(weak, manyLikes, manyViews, good),
            Collections.<MusicSelectionPolicy.PublishedSong>emptyList(), null);
        if (ranked.size() != 4 || ranked.get(ranked.size() - 1) != weak) {
            throw new AssertionError("Falha no ranking relativo por visualizações OU curtidas");
        }
        System.out.println("OK: elegibilidade, duplicidade, substituição e prioridade verificadas");
    }
}
