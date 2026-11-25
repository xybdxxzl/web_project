package com.minioj.judge;

import java.util.List;

import com.minioj.entity.LanguageType;

public interface Language {

    LanguageType type();

    String sourceFileName();

    String dockerImage();

    List<String> compileCommand();

    List<String> runCommand(int memoryLimitMb);
}
