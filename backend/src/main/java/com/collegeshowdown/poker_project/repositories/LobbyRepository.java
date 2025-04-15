package com.collegeshowdown.poker_project.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.collegeshowdown.poker_project.runtime.lobby.Lobby;

import java.util.Optional;

@Repository
public interface LobbyRepository extends JpaRepository<Lobby, Integer> {
    // Find a game by ID
    Optional<Lobby> findById(int id);

}
