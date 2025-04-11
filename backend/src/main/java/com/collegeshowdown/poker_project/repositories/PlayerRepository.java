package com.collegeshowdown.poker_project.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.collegeshowdown.poker_project.model.Player;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Integer> {
    // Find a player by ID
    Optional<Player> findById(int id);

    // Find players by name
    Optional<Player> findByName(String name);

    // Find players by university
    List<Player> findByUniversity(String university);

    // Find players by email
    Optional<Player> findByEmail(String email);
}