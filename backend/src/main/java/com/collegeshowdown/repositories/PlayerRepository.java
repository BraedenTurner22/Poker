package com.collegeshowdown.poker_project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.collegeshowdown.poker_project.model.Player;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, String> {
    // Optional: Custom query to find a player by name
    Optional<Player> findById(int id);
}
