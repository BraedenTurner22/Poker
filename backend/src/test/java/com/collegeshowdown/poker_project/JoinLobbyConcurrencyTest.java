package com.collegeshowdown.poker_project;

import com.collegeshowdown.poker_project.domain.lobby.StakesLevel;
import com.collegeshowdown.poker_project.models.PlayerRecord;
import com.collegeshowdown.poker_project.repositories.PlayerRepository;
import com.collegeshowdown.poker_project.service.GameStateBuilder;
import com.collegeshowdown.poker_project.services.DealerService;
import com.collegeshowdown.poker_project.services.LobbyGameService;
import com.collegeshowdown.poker_project.services.LobbyManagerService;
import com.collegeshowdown.poker_project.services.PotManagerService;
import com.collegeshowdown.poker_project.services.WinnerService;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Regression test for a real production bug: two near-simultaneous
 * joinLobby() calls for the same account (e.g. a slow/cold-starting first
 * request plus a frustrated retry) could both pass the "already seated?"
 * scan before either had written a seat, seating the same account twice —
 * one account playing against itself. Fixed by a per-player lock spanning
 * the scan-and-seat operation; this test fires many concurrent joins for
 * one account and asserts only one seat is ever created.
 */
class JoinLobbyConcurrencyTest {

    private static PlayerRecord player(int id) {
        PlayerRecord record = new PlayerRecord("p" + id, "p" + id + "@umass.edu",
                "x", "umass.edu", "UMass", "LOCAL");
        try {
            Field f = PlayerRecord.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(record, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        record.updateChips(10_000);
        return record;
    }

    @Test
    void concurrentJoinsForSameAccountNeverProduceTwoSeats() throws InterruptedException {
        LobbyGameService lobbyGameService = new LobbyGameService(
                mock(DealerService.class),
                mock(PotManagerService.class),
                mock(WinnerService.class),
                mock(GameStateBuilder.class),
                mock(SimpMessagingTemplate.class),
                null); // LobbyManagerService back-reference unused on the join path

        LobbyManagerService lobbyManagerService =
                new LobbyManagerService(lobbyGameService, mock(PlayerRepository.class));

        PlayerRecord record = player(1);

        int threadCount = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<LobbyManagerService.LobbyAssignment> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    results.add(lobbyManagerService.joinLobby(record, StakesLevel.LOW, false, 0));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown(); // release every thread at once to maximize contention
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(threadCount, results.size());

        Set<String> distinctSeats = results.stream()
                .map(a -> a.lobby().getId() + "#" + a.seat())
                .collect(Collectors.toSet());
        assertEquals(1, distinctSeats.size(),
                "Every concurrent join for the same account must resolve to the same single seat");
    }
}
