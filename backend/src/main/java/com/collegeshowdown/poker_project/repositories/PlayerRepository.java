package com.collegeshowdown.poker_project.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.collegeshowdown.poker_project.models.PlayerRecord;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<PlayerRecord, Integer> {
    Optional<PlayerRecord> findByEmail(String email);
    List<PlayerRecord> findByCollegeDomain(String collegeDomain);
    boolean existsByEmail(String email);

    // Every leaderboard ordering breaks profit ties by id, so the paged top-10
    // query and the full-table "where am I?" scan agree on one order —
    // otherwise a tied player can get a different rank in each, or vanish
    // from both (the frontend only shows the "you" row when rank > 10).

    @Query("SELECT p FROM PlayerRecord p ORDER BY p.totalChipDifference DESC, p.id ASC")
    List<PlayerRecord> findTopPlayersByAllTimeProfit(Pageable pageable);

    @Query("SELECT p FROM PlayerRecord p ORDER BY p.monthlyChipDifference DESC, p.id ASC")
    List<PlayerRecord> findTopPlayersByMonthlyProfit(Pageable pageable);

    // School totals and "your rank" are computed in the database. These
    // endpoints are public (no auth needed), and previously every call loaded
    // the entire players table (every row, password hash included) into memory.

    /** Per school: [collegeDomain, collegeName, summed all-time profit (Long)]. */
    @Query("SELECT p.collegeDomain, MIN(p.collegeName), SUM(p.totalChipDifference) "
            + "FROM PlayerRecord p GROUP BY p.collegeDomain")
    List<Object[]> sumAllTimeProfitBySchool();

    /** Per school: [collegeDomain, collegeName, summed monthly profit (Long)]. */
    @Query("SELECT p.collegeDomain, MIN(p.collegeName), SUM(p.monthlyChipDifference) "
            + "FROM PlayerRecord p GROUP BY p.collegeDomain")
    List<Object[]> sumMonthlyProfitBySchool();

    /** Players ranked ahead of (profit, id) in the all-time ordering above; rank = this + 1. */
    @Query("SELECT COUNT(p) FROM PlayerRecord p WHERE p.totalChipDifference > :profit "
            + "OR (p.totalChipDifference = :profit AND p.id < :id)")
    long countAheadByAllTimeProfit(@Param("profit") int profit, @Param("id") Integer id);

    /** Players ranked ahead of (profit, id) in the monthly ordering above; rank = this + 1. */
    @Query("SELECT COUNT(p) FROM PlayerRecord p WHERE p.monthlyChipDifference > :profit "
            + "OR (p.monthlyChipDifference = :profit AND p.id < :id)")
    long countAheadByMonthlyProfit(@Param("profit") int profit, @Param("id") Integer id);

    @Query("SELECT p FROM PlayerRecord p WHERE p.collegeDomain = :domain ORDER BY p.totalChipDifference DESC, p.id ASC")
    List<PlayerRecord> findByCollegeDomainOrderedByAllTimeProfit(@Param("domain") String domain, Pageable pageable);

    @Query("SELECT p FROM PlayerRecord p WHERE p.collegeDomain = :domain ORDER BY p.monthlyChipDifference DESC, p.id ASC")
    List<PlayerRecord> findByCollegeDomainOrderedByMonthlyProfit(@Param("domain") String domain, Pageable pageable);

    /** Same-school players ranked ahead of (profit, id) in the all-time ordering; rank = this + 1. */
    @Query("SELECT COUNT(p) FROM PlayerRecord p WHERE p.collegeDomain = :domain AND "
            + "(p.totalChipDifference > :profit OR (p.totalChipDifference = :profit AND p.id < :id))")
    long countAheadInSchoolByAllTimeProfit(@Param("domain") String domain,
            @Param("profit") int profit, @Param("id") Integer id);

    /** Same-school players ranked ahead of (profit, id) in the monthly ordering; rank = this + 1. */
    @Query("SELECT COUNT(p) FROM PlayerRecord p WHERE p.collegeDomain = :domain AND "
            + "(p.monthlyChipDifference > :profit OR (p.monthlyChipDifference = :profit AND p.id < :id))")
    long countAheadInSchoolByMonthlyProfit(@Param("domain") String domain,
            @Param("profit") int profit, @Param("id") Integer id);

    @Modifying
    @Transactional
    @Query("UPDATE PlayerRecord p SET p.monthlyChipDifference = 0")
    void resetMonthlyChipDifferences();

    // ─── Bank balance ────────────────────────────────────────────────────────────
    // Chip movements between the bank and a table are single atomic UPDATEs,
    // not load → mutate entity → save(). The PlayerRecord a seated
    // ConnectedPlayer holds was loaded once at join time and goes stale as
    // soon as anything else touches the row (a rebuy request loads its own
    // copy, the monthly reset is a bulk UPDATE) — saving that stale copy
    // back at cash-out silently overwrote those changes.

    /**
     * Moves {@code amount} chips out of the bank. Returns 1 on success, 0 if
     * the bank holds fewer than {@code amount} chips (nothing is changed).
     */
    @Modifying
    @Transactional
    @Query("UPDATE PlayerRecord p SET p.totalChips = p.totalChips - :amount "
            + "WHERE p.id = :id AND p.totalChips >= :amount")
    int withdrawFromBank(@Param("id") Integer id, @Param("amount") int amount);

    /**
     * Moves {@code amount} chips into the bank and adds {@code profit}
     * (chips cashed out minus chips bought in for that seat) to every
     * profit stat. Returns the number of rows updated.
     */
    @Modifying
    @Transactional
    @Query("UPDATE PlayerRecord p SET p.totalChips = p.totalChips + :amount, "
            + "p.totalChipDifference = p.totalChipDifference + :profit, "
            + "p.oneWeekChipDifference = p.oneWeekChipDifference + :profit, "
            + "p.monthlyChipDifference = p.monthlyChipDifference + :profit "
            + "WHERE p.id = :id")
    int depositToBank(@Param("id") Integer id, @Param("amount") int amount, @Param("profit") int profit);

    /**
     * Refreshes only the display name. Not load → setName → save(): with a
     * detached entity save() is a merge, which copies every field of the
     * snapshot (bank balance and profit included) back over the current row.
     */
    @Modifying
    @Transactional
    @Query("UPDATE PlayerRecord p SET p.name = :name WHERE p.id = :id")
    int updateName(@Param("id") Integer id, @Param("name") String name);

    /** Current bank balance read straight from the database (never a cached entity). */
    @Query("SELECT p.totalChips FROM PlayerRecord p WHERE p.id = :id")
    Optional<Integer> findTotalChipsById(@Param("id") Integer id);
}
