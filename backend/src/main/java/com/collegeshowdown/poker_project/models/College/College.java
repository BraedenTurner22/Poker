package com.collegeshowdown.poker_project.models.College;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity @Table(name="colleges")
public class College {
    @Id
    @GeneratedValue
    private Long id;

    public String name;
    public String domain;
    public String collegeLogoImagePath; // HTTP path
}
