package com.collegeshowdown.poker_project.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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

    @Query("SELECT p FROM PlayerRecord p ORDER BY p.totalChipDifference DESC")
    List<PlayerRecord> findTopPlayersByAllTimeProfit(Pageable pageable);

    @Query("SELECT p FROM PlayerRecord p ORDER BY p.monthlyChipDifference DESC")
    List<PlayerRecord> findTopPlayersByMonthlyProfit(Pageable pageable);

    /** All players ordered by total diff — used to compute school grouping in Java. */
    @Query("SELECT p FROM PlayerRecord p ORDER BY p.totalChipDifference DESC")
    List<PlayerRecord> findAllOrderedByAllTimeProfit();

    /** All players ordered by monthly diff — used to compute school grouping in Java. */
    @Query("SELECT p FROM PlayerRecord p ORDER BY p.monthlyChipDifference DESC")
    List<PlayerRecord> findAllOrderedByMonthlyProfit();

    @Query("SELECT p FROM PlayerRecord p WHERE p.collegeDomain = :domain ORDER BY p.totalChipDifference DESC")
    List<PlayerRecord> findByCollegeDomainOrderedByAllTimeProfit(String domain);

    @Query("SELECT p FROM PlayerRecord p WHERE p.collegeDomain = :domain ORDER BY p.monthlyChipDifference DESC")
    List<PlayerRecord> findByCollegeDomainOrderedByMonthlyProfit(String domain);

    @Modifying
    @Transactional
    @Query("UPDATE PlayerRecord p SET p.monthlyChipDifference = 0")
    void resetMonthlyChipDifferences();
}
