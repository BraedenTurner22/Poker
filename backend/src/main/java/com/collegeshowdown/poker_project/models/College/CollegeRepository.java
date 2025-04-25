package com.collegeshowdown.poker_project.models.College;

import org.springframework.data.jpa.repository.JpaRepository;
import com.collegeshowdown.poker_project.models.College.College;
import java.util.Optional;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface CollegeRepository extends JpaRepository<College, Long> {
    @Query("SELECT s from School s JOIN s.domains d WHERE d.domain = :domain")
    Optional<College> findByDomain(@Param("domain") String domain);
}
