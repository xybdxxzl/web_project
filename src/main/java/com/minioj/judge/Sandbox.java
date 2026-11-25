package com.minioj.judge;

import java.io.IOException;
import java.nio.file.Path;

import com.minioj.entity.LanguageType;

public interface Sandbox {

    Result compile(Language language, Workspace workspace, int timeLimitMs, int memoryLimitMb)
            throws IOException, InterruptedException;

    Result run(Language language, Workspace workspace, int caseNumber, String input, int timeLimitMs, int memoryLimitMb)
            throws IOException, InterruptedException;

    default boolean isMemoryLimitExceeded(Language language, Result result) {
        return language.type() == LanguageType.JAVA && result.stderr().contains("OutOfMemoryError");
    }

    record Workspace(long submissionId, Path directory) {
    }

    record Result(boolean timedOut, boolean outputLimitExceeded, int exitCode, long timeUsedMs,
                  String stdout, String stderr) {
    }
}
