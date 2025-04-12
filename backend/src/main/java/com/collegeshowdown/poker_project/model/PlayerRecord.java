package com.collegeshowdown.poker_project.model;

import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.collegeshowdown.poker_project.runtime.card.Card;
import com.collegeshowdown.poker_project.runtime.player.HandRank;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import java.util.ArrayList;
import java.io.Serializable;

@Entity
public class PlayerRecord implements Serializable {
    private final static Logger logger = LoggerFactory.getLogger(PlayerRecord.class);
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false, unique = true)
    private int id;

    private String name;
    private String email;
    private String university;
    private String imageURL;
    private int chips;

    public PlayerRecord() {
    }

    public PlayerRecord(int id, String name, String email, String university, String imageURL) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.university = university;
        this.imageURL = imageURL;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUniversity() {
        return university;
    }

    public void setUniversity(String university) {
        this.university = university;
    }

    public String getImageURL() {
        return imageURL;
    }

    public void setImageURL(String imageURL) {
        this.imageURL = imageURL;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n");
        stringBuilder.append("Player{");
        stringBuilder.append("\nid = " + id);
        stringBuilder.append("\nname = " + name);
        stringBuilder.append("\nemail = " + email);
        stringBuilder.append("\nuniversity = " + university);
        stringBuilder.append("\nimageURL = " + imageURL);
        stringBuilder.append("\nchips = " + chips);
        stringBuilder.append("\n}");
        return stringBuilder.toString();
    }
}
