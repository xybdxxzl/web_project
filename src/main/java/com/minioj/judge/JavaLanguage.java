package com.minioj.judge;

import java.util.List;

import com.minioj.entity.LanguageType;
import org.springframework.stereotype.Component;

@Component
public class JavaLanguage implements Language {

    @Override
    public LanguageType type() {
        return LanguageType.JAVA;
    }

    @Override
    public String sourceFileName() {
        return "Main.java";
    }

    @Override
    public String dockerImage() {
        return "eclipse-temurin:17";
    }

    @Override
    public List<String> compileCommand() {
        return List.of("javac", "-encoding", "UTF-8", "-proc:none", "-classpath", ".", sourceFileName());
    }

    @Override
    public List<String> runCommand(int memoryLimitMb) {
        return List.of("java", "-Dfile.encoding=UTF-8", "-Xmx" + memoryLimitMb + "m",
                "-cp", ".", "Main");
    }
}
