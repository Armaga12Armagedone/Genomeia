package io.github.some_example_name.lwjgl3;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Locale;

/** Ассоциация только у упакованной игры: запуск из IDE не привязывает .map к build/classes. */
final class WindowsFileAssociations {
    private static final String CLASSES = "HKCU\\Software\\Classes\\";
    private static final String TYPE = "Genomeia.Map";

    static void register() {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) return;
        try {
            File jar = new File(Lwjgl3Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!jar.isFile() || !jar.getName().endsWith(".jar")) return;
            File javaExecutable = new File(System.getProperty("java.home"), "bin/javaw.exe");
            String command = quote(javaExecutable.getAbsolutePath()) + " -jar " + quote(jar.getAbsolutePath()) + " \"%1\"";
            // Уже зарегистрированная команда не требует записи и лишних запусков reg.exe.
            String current = run("query", CLASSES + TYPE + "\\shell\\open\\command", "/ve");
            if (current != null && current.contains(command)) return;
            // .reg передаёт кавычки внутри команды без повторного разбора reg.exe /d.
            // Это важно для путей с пробелами и для аргумента "%1".
            String registry = "Windows Registry Editor Version 5.00\r\n\r\n"
                    + key(TYPE) + "@=\"Genomeia map\"\r\n\r\n"
                    + key(TYPE + "\\shell\\open\\command") + "@=" + regString(command) + "\r\n\r\n"
                    + key(".map\\OpenWithProgids") + "\"" + TYPE + "\"=\"\"\r\n\r\n";
            // Не отбираем .map у других программ и не обходим пользовательский UserChoice.
            if (run("query", "HKCR\\.map", "/ve") == null) {
                registry += key(".map") + "@=\"" + TYPE + "\"\r\n";
            }
            // StartupHelper меняет java.io.tmpdir ещё до загрузки LWJGL, который обычно
            // создаёт этот каталог. На самом первом запуске его пока может не быть.
            java.nio.file.Path tempDir = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"));
            java.nio.file.Files.createDirectories(tempDir);
            java.nio.file.Path temporary = java.nio.file.Files.createTempFile(tempDir, "genomeia-map-", ".reg");
            try {
                java.nio.file.Files.writeString(temporary, "\ufeff" + registry, java.nio.charset.StandardCharsets.UTF_16LE);
                if (run("import", temporary.toString()) == null) {
                    throw new java.io.IOException("не удалось зарегистрировать .map");
                }
            } finally {
                java.nio.file.Files.deleteIfExists(temporary);
            }
        } catch (Exception e) {
            System.err.println("[MapAssociation] " + e.getMessage());
        }
    }

    private static String quote(String value) { return "\"" + value + "\""; }

    private static String key(String suffix) {
        return "[HKEY_CURRENT_USER\\Software\\Classes\\" + suffix + "]\r\n";
    }

    private static String regString(String value) {
        return quote(value.replace("\\", "\\\\").replace("\"", "\\\""));
    }

    private static String run(String... args) throws Exception {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("reg.exe");
        java.util.Collections.addAll(command, args);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), Charset.defaultCharset());
        return process.waitFor() == 0 ? output : null;
    }
}
