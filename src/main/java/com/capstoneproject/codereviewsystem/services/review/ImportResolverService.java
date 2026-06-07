package com.capstoneproject.codereviewsystem.services.review;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;


@Slf4j
@Service
public class ImportResolverService {

    @Value("${app.storage.local.base-path:uploads}")
    private String basePath;

    // ── Java / Kotlin ──────────────────────────────────────────────────────
    // import com.example.Foo  or  import com.example.*
    private static final Pattern JAVA_IMPORT =
            Pattern.compile("^\\s*import\\s+([\\w.]+(?:\\.\\*)?);?\\s*$", Pattern.MULTILINE);

    // ── Python ────────────────────────────────────────────────────────────
    // from app.module import x  OR  from .relative import x
    private static final Pattern PYTHON_FROM_IMPORT =
            Pattern.compile("^\\s*from\\s+(\\.?[\\w.]+)\\s+import\\s+", Pattern.MULTILINE);

    // ── JS / TS ───────────────────────────────────────────────────────────
    // import ... from './relative/path'  or  import './side-effect'
    private static final Pattern JS_IMPORT =
            Pattern.compile("(?:import\\s+[^;]*from\\s+|import\\s+)['\"]([./][^'\"]+)['\"]",
                    Pattern.MULTILINE);

    // ── Go ────────────────────────────────────────────────────────────────
    // import "path/to/pkg"  or  "path/to/pkg" inside import ( ... )
    private static final Pattern GO_IMPORT =
            Pattern.compile("\"([a-zA-Z0-9_./]+)\"", Pattern.MULTILINE);

    // ── C / C++ ───────────────────────────────────────────────────────────
    // #include "local.h"  (skip #include <system.h>)
    private static final Pattern C_INCLUDE =
            Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"", Pattern.MULTILINE);

    public Set<String> resolveImports(String storagePath, List<String> changedFiles) {
        Path root = Paths.get(basePath, storagePath.replace("/", File.separator));
        Set<String> resolved = new LinkedHashSet<>();

        for (String changedFile : changedFiles) {
            try {
                Set<String> imports = resolveImportsForFile(root, changedFile);
                resolved.addAll(imports);
            } catch (Exception e) {
                log.warn("Import resolution failed for {}: {}", changedFile, e.getMessage());
            }
        }

        resolved.removeAll(new HashSet<>(changedFiles));

        log.debug("Import resolution: changedFiles={} importedFiles={}",
                changedFiles.size(), resolved.size());
        return resolved;
    }


    private Set<String> resolveImportsForFile(Path root, String relativePath) throws IOException {
        Path file = root.resolve(relativePath);
        if (!Files.exists(file) || !Files.isRegularFile(file)) return Set.of();

        String content = Files.readString(file);
        String ext = extension(relativePath);

        return switch (ext) {
            case "java"         -> resolveJavaImports(root, content);
            case "kt", "kts"    -> resolveKotlinImports(root, content);
            case "py"           -> resolvePythonImports(root, relativePath, content);
            case "js", "jsx",
                 "ts", "tsx",
                 "mjs", "cjs"   -> resolveJsImports(root, relativePath, content);
            case "go"           -> resolveGoImports(root, relativePath, content);
            case "c", "cpp",
                 "cc", "cxx",
                 "h", "hpp"     -> resolveCImports(root, relativePath, content);
            default             -> Set.of();
        };
    }


    private Set<String> resolveJavaImports(Path root, String content) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = JAVA_IMPORT.matcher(content);
        while (m.find()) {
            String fqn = m.group(1);
            if (fqn.endsWith(".*")) {
                String pkg = fqn.substring(0, fqn.length() - 2);
                String dir = pkg.replace('.', File.separatorChar);
                Path pkgDir = root.resolve(dir);
                addFilesInDir(root, pkgDir, "java", found);
            } else {
                String path = fqn.replace('.', File.separatorChar) + ".java";
                addIfExists(root, path, found);
            }
        }
        return found;
    }


    private Set<String> resolveKotlinImports(Path root, String content) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = JAVA_IMPORT.matcher(content); // same pattern
        while (m.find()) {
            String fqn = m.group(1);
            if (!fqn.endsWith(".*")) {
                String ktPath   = fqn.replace('.', File.separatorChar) + ".kt";
                String javaPath = fqn.replace('.', File.separatorChar) + ".java";
                if (!addIfExists(root, ktPath, found))
                    addIfExists(root, javaPath, found);
            }
        }
        return found;
    }


    private Set<String> resolvePythonImports(Path root, String filePath, String content) {
        Set<String> found = new LinkedHashSet<>();
        String dir = filePath.contains("/")
                ? filePath.substring(0, filePath.lastIndexOf('/'))
                : "";

        Matcher m = PYTHON_FROM_IMPORT.matcher(content);
        while (m.find()) {
            String module = m.group(1);
            String resolved = resolvePythonModule(root, dir, module);
            if (resolved != null) found.add(resolved);
        }
        return found;
    }

    private String resolvePythonModule(Path root, String currentDir, String module) {
        if (module.startsWith(".")) {
            String rel = module.substring(1).replace('.', File.separatorChar);
            String candidate = (currentDir.isEmpty() ? "" : currentDir + File.separator)
                    + rel + ".py";
            if (Files.exists(root.resolve(candidate))) return toForwardSlash(candidate);
            candidate = (currentDir.isEmpty() ? "" : currentDir + File.separator)
                    + rel + File.separator + "__init__.py";
            if (Files.exists(root.resolve(candidate))) return toForwardSlash(candidate);
        } else {
            String path = module.replace('.', File.separatorChar) + ".py";
            if (Files.exists(root.resolve(path))) return toForwardSlash(path);
            path = module.replace('.', File.separatorChar)
                    + File.separator + "__init__.py";
            if (Files.exists(root.resolve(path))) return toForwardSlash(path);
        }
        return null;
    }


    private Set<String> resolveJsImports(Path root, String filePath, String content) {
        Set<String> found = new LinkedHashSet<>();
        String dir = filePath.contains("/")
                ? filePath.substring(0, filePath.lastIndexOf('/'))
                : "";

        Matcher m = JS_IMPORT.matcher(content);
        while (m.find()) {
            String importPath = m.group(1);
            if (importPath.contains("node_modules")) continue;

            String resolved = resolveJsPath(root, dir, importPath);
            if (resolved != null) found.add(resolved);
        }
        return found;
    }

    private String resolveJsPath(Path root, String currentDir, String importPath) {
        String[] parts = (currentDir + "/" + importPath).split("/");
        Deque<String> stack = new ArrayDeque<>();
        for (String p : parts) {
            if (p.equals("..")) { if (!stack.isEmpty()) stack.pollLast(); }
            else if (!p.isEmpty() && !p.equals(".")) stack.addLast(p);
        }
        String base = String.join(File.separator, stack);

        for (String ext : List.of("", ".js", ".ts", ".jsx", ".tsx", ".mjs")) {
            Path candidate = root.resolve(base + ext);
            if (Files.isRegularFile(candidate))
                return toForwardSlash(root.relativize(candidate).toString());
        }
        for (String idx : List.of("index.js", "index.ts", "index.jsx", "index.tsx")) {
            Path candidate = root.resolve(base + File.separator + idx);
            if (Files.isRegularFile(candidate))
                return toForwardSlash(root.relativize(candidate).toString());
        }
        return null;
    }


    private Set<String> resolveGoImports(Path root, String filePath, String content) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = GO_IMPORT.matcher(content);
        while (m.find()) {
            String pkg = m.group(1);
            if (!pkg.contains("/")) continue; // stdlib single-word packages
            String dir = pkg.replace('/', File.separatorChar);
            Path pkgDir = root.resolve(dir);
            addFilesInDir(root, pkgDir, "go", found);
        }
        return found;
    }

    private Set<String> resolveCImports(Path root, String filePath, String content) {
        Set<String> found = new LinkedHashSet<>();
        String dir = filePath.contains("/")
                ? filePath.substring(0, filePath.lastIndexOf('/'))
                : "";

        Matcher m = C_INCLUDE.matcher(content);
        while (m.find()) {
            String include = m.group(1);
            String relative = (dir.isEmpty() ? "" : dir + File.separator)
                    + include.replace('/', File.separatorChar);
            if (!addIfExists(root, relative, found)) {
                addIfExists(root, include.replace('/', File.separatorChar), found);
            }
        }
        return found;
    }

    private boolean addIfExists(Path root, String relativePath, Set<String> found) {
        Path candidate = root.resolve(relativePath);
        if (Files.isRegularFile(candidate)) {
            found.add(toForwardSlash(relativePath));
            return true;
        }
        return false;
    }

    private void addFilesInDir(Path root, Path dir, String extension, Set<String> found) {
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith("." + extension))
                  .forEach(p -> found.add(toForwardSlash(root.relativize(p).toString())));
        } catch (IOException e) {
            log.warn("Could not list directory {}: {}", dir, e.getMessage());
        }
    }

    private String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1).toLowerCase() : "";
    }

    private String toForwardSlash(String path) {
        return path.replace(File.separatorChar, '/');
    }
}
