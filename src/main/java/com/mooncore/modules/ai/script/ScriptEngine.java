package com.mooncore.modules.ai.script;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Compile et exécute une classe Java {@link MoonScript} à chaud (mode développeur).
 * Nécessite un JDK (javac via {@link ToolProvider#getSystemJavaCompiler()}). La classe
 * est compilée dans un dossier temporaire puis chargée par un classloader enfant de
 * celui du plugin (accès à Bukkit + MoonCore). Exécution sur le thread appelant.
 */
public final class ScriptEngine {

    public static final String CLASS_NAME = "GeneratedScript";

    public boolean available(String javacPath) {
        return ToolProvider.getSystemJavaCompiler() != null || resolveJavac(javacPath) != null;
    }

    /** @return null si OK, sinon un message d'erreur (compilation ou exécution). */
    public String compileAndRun(String source, Plugin plugin, CommandSender sender, String javacPath) {
        try {
            File dir = Files.createTempDirectory("mooncore-script").toFile();
            dir.deleteOnExit();
            File src = new File(dir, CLASS_NAME + ".java");
            Files.writeString(src.toPath(), source, StandardCharsets.UTF_8);
            String cp = buildClasspath(plugin);

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            String compileError = (compiler != null)
                    ? compileInProcess(compiler, src, dir, cp)
                    : compileExternal(resolveJavac(javacPath), src, dir, cp);
            if (compileError != null) return compileError;

            try (URLClassLoader cl = new URLClassLoader(new URL[]{dir.toURI().toURL()},
                    plugin.getClass().getClassLoader())) {
                Class<?> clazz = Class.forName(CLASS_NAME, true, cl);
                Object inst = clazz.getDeclaredConstructor().newInstance();
                if (!(inst instanceof MoonScript script)) {
                    return "La classe ne implémente pas MoonScript.";
                }
                script.run(plugin, sender);
            }
            return null;
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            return "Exécution : " + c.getClass().getSimpleName() + " — " + c.getMessage();
        }
    }

    private String compileInProcess(JavaCompiler compiler, File src, File dir, String cp) throws Exception {
        DiagnosticCollector<JavaFileObject> diag = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diag, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjects(src);
            List<String> options = List.of("-classpath", cp, "-d", dir.getAbsolutePath(), "--release", "21");
            boolean ok = compiler.getTask(null, fm, diag, options, null, units).call();
            if (ok) return null;
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (var d : diag.getDiagnostics()) {
                if (n++ >= 6) { sb.append("…\n"); break; }
                sb.append("L").append(d.getLineNumber()).append(": ").append(d.getMessage(null)).append('\n');
            }
            return "Compilation échouée :\n" + sb;
        }
    }

    private String compileExternal(String javac, File src, File dir, String cp) {
        if (javac == null) {
            return "Aucun compilateur (javac) trouvé. Installe un JDK 21 ou règle 'javac-path' dans ai-assistant.yml.";
        }
        try {
            Process p = new ProcessBuilder(javac, "-classpath", cp, "-d", dir.getAbsolutePath(),
                    "--release", "21", src.getAbsolutePath())
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = p.waitFor();
            return code == 0 ? null : "Compilation échouée :\n" + (out.isBlank() ? "(code " + code + ")" : out);
        } catch (Exception e) {
            return "javac externe : " + e.getMessage();
        }
    }

    /** Localise un exécutable javac : chemin configuré → java.home → null. */
    private String resolveJavac(String configured) {
        if (configured != null && !configured.isBlank()) {
            File f = new File(configured);
            if (f.isFile()) return f.getAbsolutePath();
        }
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        File jh = new File(System.getProperty("java.home"), "bin/javac" + (win ? ".exe" : ""));
        if (jh.isFile()) return jh.getAbsolutePath();
        return null;
    }

    /** Classpath de compilation : classpath JVM + jar Bukkit/Paper + jar du plugin. */
    private String buildClasspath(Plugin plugin) {
        List<String> parts = new ArrayList<>();
        String sys = System.getProperty("java.class.path");
        if (sys != null && !sys.isBlank()) parts.add(sys);
        addCodeSource(parts, org.bukkit.Bukkit.class);   // API serveur
        addCodeSource(parts, plugin.getClass());          // MoonCore + libs shadées
        addCodeSource(parts, MoonScript.class);
        return String.join(File.pathSeparator, parts);
    }

    private void addCodeSource(List<String> parts, Class<?> ref) {
        try {
            var cs = ref.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                parts.add(new File(cs.getLocation().toURI()).getAbsolutePath());
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
