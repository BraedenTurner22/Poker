package com.collegeshowdown.poker_project.services;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.File;
import java.util.*;

import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import com.collegeshowdown.poker_project.models.College.College;
import com.collegeshowdown.poker_project.models.College.CollegeRepository;

@Service
public class CollegeJSONService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CollegeRepository repo;

    List<College> schoolByName;

    public CollegeJSONService(CollegeRepository repo) {
        this.repo= repo;
    }

    @PostConstruct
    public void loadCollegeList() {
        try {
            schoolByName = Arrays.asList(objectMapper.readValue(new File("schools.json"), College[].class));
        } catch (Throwable e) {
            return; // fix.
        }
    }

    public College collegeByDomain(String domain) {
        return repo.findByDomain(domain).orElseGet(() ->
            schoolByName.stream()
            .filter(s -> s.domains.contains(domain))
            .findFirst()
            .map(repo::save)
            .orElse(null)
        );
    }
}
