package com.collegeshowdown.poker_project.services;

import com.collegeshowdown.poker_project.models.PlayerRecord;
import com.collegeshowdown.poker_project.repositories.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        List<PlayerRecord> all = range == Range.MONTHLY
                ? playerRepository.findAllOrderedByMonthlyProfit()
                : playerRepository.findAllOrderedByAllTimeProfit();

        // Aggregate per school
        Map<String, Long>   profitByDomain = new LinkedHashMap<>();
        Map<String, String> nameByDomain   = new LinkedHashMap<>();
        for (PlayerRecord p : all) {
            profitByDomain.merge(p.getCollegeDomain(), profitOf(p, range), Long::sum);
            nameByDomain.putIfAbsent(p.getCollegeDomain(), p.getCollegeName());
        }

        // Sort domains by profit descending
        List<String> sorted = profitByDomain.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();

        List<SchoolEntry> top10 = new ArrayList<>();
        SchoolEntry mySchool = null;
        for (int i = 0; i < sorted.size(); i++) {
            String domain  = sorted.get(i);
            long   profit  = profitByDomain.get(domain);
            String name    = nameByDomain.get(domain);
            int    rank    = i + 1;
            SchoolEntry e  = new SchoolEntry(name, domain, profit, rank);
            if (i < 10) top10.add(e);
            if (domain.equals(myCollegeDomain)) mySchool = e;
        }
        return new SchoolLeaderboardResult(top10, mySchool);
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

        // If user isn't in top 10, find their rank
        if (me == null && myUserId != null) {
            String sortProperty = range == Range.MONTHLY ? "monthlyChipDifference" : "totalChipDifference";
            List<PlayerRecord> all = playerRepository.findAll(Sort.by(Sort.Direction.DESC, sortProperty));
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).getId().equals(myUserId)) {
                    PlayerRecord p = all.get(i);
                    me = new PlayerEntry(p.getName(), p.getCollegeName(),
                            p.getCollegeDomain(), profitOf(p, range), i + 1);
                    break;
                }
            }
        }
        return new PlayerLeaderboardResult(top10, me);
    }

    // ─── Player leaderboard (within your school) ─────────────────────────────────

    public PlayerLeaderboardResult schoolPlayerLeaderboard(String collegeDomain, Integer myUserId, Range range) {
        if (collegeDomain == null) return new PlayerLeaderboardResult(List.of(), null);

        List<PlayerRecord> all = range == Range.MONTHLY
                ? playerRepository.findByCollegeDomainOrderedByMonthlyProfit(collegeDomain)
                : playerRepository.findByCollegeDomainOrderedByAllTimeProfit(collegeDomain);

        List<PlayerEntry> top10 = new ArrayList<>();
        PlayerEntry me = null;
        for (int i = 0; i < all.size(); i++) {
            PlayerRecord p = all.get(i);
            PlayerEntry e = new PlayerEntry(p.getName(), p.getCollegeName(),
                    p.getCollegeDomain(), profitOf(p, range), i + 1);
            if (i < 10) top10.add(e);
            if (p.getId().equals(myUserId)) me = e;
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
