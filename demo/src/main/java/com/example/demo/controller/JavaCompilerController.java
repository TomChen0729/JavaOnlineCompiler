package com.example.demo.controller;

import com.example.demo.util.InMemoryClassLoader;
import com.example.demo.util.InMemoryJavaFileObject;
import org.springframework.web.bind.annotation.*;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.*;

@RestController
@RequestMapping("/compile")
public class JavaCompilerController {

    @PostMapping
    public String compileAndRun(@RequestBody Map<String, String> body) throws Exception {
        String code = body.get("code");

        // 提取 public class 的名稱
        Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)");
        Matcher matcher = pattern.matcher(code);
        if (!matcher.find()) {
            return "錯誤：找不到 public class 的名稱。請確認輸入的 Java 程式碼中包含正確的 public class 宣告。";
        }
        String className = matcher.group(1); // 取得類別名稱

        // 建立虛擬 Java 檔案
        JavaFileObject fileObject = new InMemoryJavaFileObject(className, code);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

        // 編譯任務
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, null, null, List.of(fileObject));
        if (!task.call()) {
            StringBuilder errors = new StringBuilder("編譯失敗:\n");
            for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
                errors.append(diagnostic.getMessage(null)).append("\n");
            }
            return errors.toString();
        }

        // 執行 main 方法
        InMemoryClassLoader classLoader = new InMemoryClassLoader();
        Class<?> clazz = classLoader.loadClass(className);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(outputStream));

        try {
            Method main = clazz.getMethod("main", String[].class);
            main.invoke(null, (Object) new String[]{});
        } finally {
            System.setOut(oldOut);
        }

        return outputStream.toString();
    }
}
