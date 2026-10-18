package com.collegeshowdown.poker_project.services;

import com.collegeshowdown.poker_project.models.PlayerRecord;
import com.collegeshowdown.poker_project.repositories.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);
    private final PlayerRepository playerRepository;

    public LeaderboardService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public enum Range { ALL_TIME, MONTHLY }

    private static long profitOf(PlayerRecord p, Range range) {
        return range == Range.MONTHLY ? p.getMonthlyChipDifference() : p.getTotalChipDifference();
    }

    // ─── School leaderboard ───────────────────────────────────────────────────────

    public record SchoolEntry(String collegeName, String collegeDomain,
                              long profit, int rank) {}

    public record SchoolLeaderboardResult(List<SchoolEntry> top10, SchoolEntry mySchool) {}

    public SchoolLeaderboardResult schoolLeaderboard(String myCollegeDomain, Range range) {
        List<Object[]> rows = range == Range.MONTHLY
                ? playerRepository.sumMonthlyProfitBySchool()
                : playerRepository.sumAllTimeProfitBySchool();

        // Sort schools by profit descending, ties by domain
        List<Object[]> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.<Object[]>comparingLong(r -> sumOf(r[2])).reversed()
                .thenComparing(r -> (String) r[0]));

        List<SchoolEntry> top10 = new ArrayList<>();
        SchoolEntry mySchool = null;
        for (int i = 0; i < sorted.size(); i++) {
            Object[] r     = sorted.get(i);
            String domain  = (String) r[0];
            SchoolEntry e  = new SchoolEntry((String) r[1], domain, sumOf(r[2]), i + 1);
            if (i < 10) top10.add(e);
            if (domain.equals(myCollegeDomain)) mySchool = e;
        }
        return new SchoolLeaderboardResult(top10, mySchool);
    }

    /** JPQL SUM over an Integer column is a Long (null only for an empty group). */
    private static long sumOf(Object sum) {
        return sum == null ? 0L : ((Number) sum).longValue();
    }

    // ─── Player leaderboard (global) ─────────────────────────────────────────────

    public record PlayerEntry(String name, String collegeName, String collegeDomain,
                              long profit, int rank) {}

    public record PlayerLeaderboardResult(List<PlayerEntry> top10, PlayerEntry me) {}

    public PlayerLeaderboardResult playerLeaderboard(Integer myUserId, Range range) {
        List<PlayerRecord> top = range == Range.MONTHLY
                ? playerRepository.findTopPlayersByMonthlyProfit(PageRequest.of(0, 10))
                : playerRepository.findTopPlayersByAllTimeProfit(PageRequest.of(0, 10));
        List<PlayerEntry> top10 = new ArrayList<>();
        PlayerEntry me = null;

        for (int i = 0; i < top.size(); i++) {
            PlayerRecord p = top.get(i);
            PlayerEntry e = new PlayerEntry(p.getName(), p.getCollegeName(),
                    p.getCollegeDomain(), profitOf(p, range), i + 1);
            top10.add(e);
            if (p.getId().equals(myUserId)) me = e;
        }

        // If user isn't in top 10, find their rank: everyone ahead of them in
        // the same (profit DESC, id ASC) order the top-10 page was cut from.
        if (me == null && myUserId != null) {
            PlayerRecord p = playerRepository.findById(myUserId).orElse(null);
            if (p != null) {
                int profit = (int) profitOf(p, range);
                long ahead = range == Range.MONTHLY
                        ? playerRepository.countAheadByMonthlyProfit(profit, p.getId())
                        : playerRepository.countAheadByAllTimeProfit(profit, p.getId());
                me = new PlayerEntry(p.getName(), p.getCollegeName(),
                        p.getCollegeDomain(), profit, (int) ahead + 1);
            }
        }
        return new PlayerLeaderboardResult(top10, me);
    }

    // ─── Player leaderboard (within your school) ─────────────────────────────────

    public PlayerLeaderboardResult schoolPlayerLeaderboard(String collegeDomain, Integer myUserId, Range range) {
        if (collegeDomain == null) return new PlayerLeaderboardResult(List.of(), null);

        List<PlayerRecord> top = range == Range.MONTHLY
                ? playerRepository.findByCollegeDomainOrderedByMonthlyProfit(collegeDomain, PageRequest.of(0, 10))
                : playerRepository.findByCollegeDomainOrderedByAllTimeProfit(collegeDomain, PageRequest.of(0, 10));

        List<PlayerEntry> top10 = new ArrayList<>();
        PlayerEntry me = null;
        for (int i = 0; i < top.size(); i++) {
            PlayerRecord p = top.get(i);
            PlayerEntry e = new PlayerEntry(p.getName(), p.getCollegeName(),
                    p.getCollegeDomain(), profitOf(p, range), i + 1);
            top10.add(e);
            if (p.getId().equals(myUserId)) me = e;
        }

        if (me == null && myUserId != null) {
            PlayerRecord p = playerRepository.findById(myUserId).orElse(null);
            if (p != null && collegeDomain.equals(p.getCollegeDomain())) {
                int profit = (int) profitOf(p, range);
                long ahead = range == Range.MONTHLY
                        ? playerRepository.countAheadInSchoolByMonthlyProfit(collegeDomain, profit, p.getId())
                        : playerRepository.countAheadInSchoolByAllTimeProfit(collegeDomain, profit, p.getId());
                me = new PlayerEntry(p.getName(), p.getCollegeName(),
                        p.getCollegeDomain(), profit, (int) ahead + 1);
            }
        }
        return new PlayerLeaderboardResult(top10, me);
    }

    // ─── Monthly reset ────────────────────────────────────────────────────────────

    @Scheduled(cron = "0 0 0 1 * *")
    public void resetMonthlyStats() {
        playerRepository.resetMonthlyChipDifferences();
        log.info("Monthly chip differences reset.");
    }
}
