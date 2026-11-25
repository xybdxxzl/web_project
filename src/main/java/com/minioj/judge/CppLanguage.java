package com.minioj.judge;

import java.util.List;

import com.minioj.entity.LanguageType;
import org.springframework.stereotype.Component;

@Component
public class CppLanguage implements Language {

    @Override
    public LanguageType type() {
        return LanguageType.CPP;
    }

    @Override
    public String sourceFileName() {
        return "main.cpp";
    }

    @Override
    public String dockerImage() {
        return "gcc:13";
    }

    @Override
    public List<String> compileCommand() {
        return List.of("g++", "-O2", "-std=c++17", "-o", "main", sourceFileName());
    }

    @Override
    public List<String> runCommand(int memoryLimitMb) {
        return List.of("./main");
    }
}
