package com.collegeshowdown.poker_project.models.College;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.ElementCollection;
import java.util.List;

@Entity @Table(name="colleges")
public class College {
    @Id
    @GeneratedValue
    private Long id;

    public String name;

    @ElementCollection
    public List<String> domains;
    public String collegeLogoImagePath; // HTTP path
}
