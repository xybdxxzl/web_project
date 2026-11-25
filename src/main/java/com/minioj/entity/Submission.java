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
@Table(name = "submissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long problemId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private LanguageType language;

    @Lob
    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private JudgeStatus status;

    private Long timeUsedMs;
    private Long memoryUsedKb;

    @Column(length = 4096)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Submission(Long userId, Long problemId, LanguageType language, String code) {
        this.userId = userId;
        this.problemId = problemId;
        this.language = language;
        this.code = code;
        this.status = JudgeStatus.PENDING;
        this.createdAt = Instant.now();
    }
}
