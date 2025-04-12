package com.collegeshowdown.poker_project.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.collegeshowdown.poker_project.model.PlayerRecord;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<PlayerRecord, Integer> {
    // Find a player by ID
    Optional<PlayerRecord> findById(int id);

    // Find players by name
    Optional<PlayerRecord> findByName(String name);

    // Find players by university
    List<PlayerRecord> findByUniversity(String university);

    // Find players by email
    Optional<PlayerRecord> findByEmail(String email);
}