package com.minioj.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "problems")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private int timeLimitMs;

    @Column(nullable = false)
    private int memoryLimitMb;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private Difficulty difficulty;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Problem(String title, String description, int timeLimitMs, int memoryLimitMb, Difficulty difficulty) {
        update(title, description, timeLimitMs, memoryLimitMb, difficulty);
        this.createdAt = Instant.now();
    }

    public void update(String title, String description, int timeLimitMs, int memoryLimitMb, Difficulty difficulty) {
        this.title = title;
        this.description = description;
        this.timeLimitMs = timeLimitMs;
        this.memoryLimitMb = memoryLimitMb;
        this.difficulty = difficulty;
    }
}
