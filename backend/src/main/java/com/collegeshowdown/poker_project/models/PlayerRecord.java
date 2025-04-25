package com.collegeshowdown.poker_project.models;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.collegeshowdown.poker_project.models.College.College;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity representing a player in the poker game.
 */
@Entity @Table(name = "players")
public class PlayerRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(PlayerRecord.class);

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "college", nullable = false)
    private College college;

    @Column(name = "university_logo_image_path")
    private String universityLogoImagePath = college.getCollegeLogoImagePath();

    @Column(name = "total_chips", nullable = false)
    private Integer totalChips = 0;

    @Column(name = "total_chip_difference", nullable = false)
    private Integer totalChipDifference = 0;

    @Column(name = "one_week_chip_difference", nullable = false)
    private Integer oneWeekChipDifference = 0;

    /**
     * Default constructor required by JPA
     */
    public PlayerRecord() {
    }



    /**
     * Constructs a new player with the specified details.
     */
    public PlayerRecord(Integer id, String name, String email, College college) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.college = college;
        this.totalChips = 0;
    }



    /**
     * Full constructor including totalChips
     */
    public PlayerRecord(Integer id, String name, String email, College college, Integer totalChips) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.college = college;
        this.totalChips = totalChips;
    }



    public Integer getId() {
        return id;
    }



    public void setId(Integer id) {
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



    public College getCollege() {
        return this.college;
    }



    public void setCollege(College college) {
        this.college = college;
    }



    public Integer getTotalChips() {
        return totalChips;
    }



    public void setTotalChips(Integer totalChips) {
        this.totalChips = totalChips;
    }



    /**
     * Updates the player's total chips by adding the specified amount.
     * 
     * @param chipAmount the amount to add (can be negative for subtraction)
     */
    public void updateChips(int chipAmount) {
        this.totalChips += chipAmount;
    }



    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        PlayerRecord that = (PlayerRecord) o;

        return id != null ? id.equals(that.id) : that.id == null;
    }



    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }



    @Override
    public String toString() {
        return "PlayerRecord{" + "id=" + id + ", name='" + name + '\'' + ", email='" + email + '\'' + ", college='"
                + college + '\'' + ", collegeImagePath='" + college.getCollegeLogoImagePath() + '\'' + ", totalChips="
                + totalChips + '}';
    }
}