package com.collegeshowdown.poker_project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.collegeshowdown.poker_project.model.Game;

import java.util.Optional;

@Repository
public interface GameRepository extends JpaRepository<Game, Integer> {
    // Find a game by ID
    Optional<Game> findById(int id);
    
    // Find games by name
    Optional<Game> findByName(String name);
}
