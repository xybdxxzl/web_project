package com.minioj.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "test_cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long problemId;

    @Lob
    @Column(name = "input_text", nullable = false)
    private String input;

    @Lob
    @Column(nullable = false)
    private String expectedOutput;

    @Column(name = "is_sample", nullable = false)
    private boolean sample;

    public TestCase(Long problemId, String input, String expectedOutput, boolean sample) {
        this.problemId = problemId;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.sample = sample;
    }
}
