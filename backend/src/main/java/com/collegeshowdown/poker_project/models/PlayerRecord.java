package com.collegeshowdown.poker_project.models;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity @Table(name = "players")
public class PlayerRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int STARTING_CHIPS = 1000;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "username", nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "college_domain", nullable = false)
    private String collegeDomain;

    @Column(name = "college_name", nullable = false)
    private String collegeName;

    @Column(name = "total_chips", nullable = false)
    private Integer totalChips = STARTING_CHIPS;

    @Column(name = "total_chip_difference", nullable = false)
    private Integer totalChipDifference = 0;

    @Column(name = "one_week_chip_difference", nullable = false)
    private Integer oneWeekChipDifference = 0;

    @Column(name = "monthly_chip_difference", nullable = false,
            columnDefinition = "integer not null default 0")
    private Integer monthlyChipDifference = 0;

    /** "LOCAL" (password-based) or "GOOGLE" (Google-verified .edu sign-in). Nullable for rows created before this column existed — treat null as "LOCAL". */
    @Column(name = "auth_provider")
    private String authProvider = "LOCAL";

    protected PlayerRecord() {}

    public PlayerRecord(String name, String email, String passwordHash,
            String collegeDomain, String collegeName, String authProvider) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.collegeDomain = collegeDomain;
        this.collegeName = collegeName;
        this.totalChips = STARTING_CHIPS;
        this.totalChipDifference = 0;
        this.oneWeekChipDifference = 0;
        this.monthlyChipDifference = 0;
        this.authProvider = authProvider;
    }

    public void updateChips(int amount) {
        this.totalChips += amount;
        this.totalChipDifference += amount;
        this.oneWeekChipDifference += amount;
        this.monthlyChipDifference += amount;
    }

    public Integer getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getCollegeDomain() { return collegeDomain; }
    public void setCollegeDomain(String collegeDomain) { this.collegeDomain = collegeDomain; }
    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String collegeName) { this.collegeName = collegeName; }
    public Integer getTotalChips() { return totalChips; }
    public void setTotalChips(Integer totalChips) { this.totalChips = totalChips; }
    public Integer getTotalChipDifference() { return totalChipDifference; }
    public Integer getOneWeekChipDifference() { return oneWeekChipDifference; }
    public Integer getMonthlyChipDifference() { return monthlyChipDifference; }
    public void setMonthlyChipDifference(Integer v) { this.monthlyChipDifference = v; }
    public String getAuthProvider() { return authProvider; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerRecord that = (PlayerRecord) o;
        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() { return id != null ? id.hashCode() : 0; }

    @Override
    public String toString() {
        return "PlayerRecord{id=" + id + ", name='" + name + "', college='" + collegeName + "', chips=" + totalChips + "}";
    }
}
