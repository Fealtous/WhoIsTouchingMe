package dev.fealtous;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.ASM9;

public class Main {

    static String currentTarget = null;
    static String targetPackage = null;
    static Set<String> hits = new HashSet<>();
    public static void main(String[] args)  {
        targetPackage = args[1];
        try {
            File f = new File(args[0]);
            if (f.exists() && f.getName().endsWith(".jar")) {
                load(f);
            } else if (f.exists() && f.isDirectory()) {
                loadMany(f);
            }
            hits.forEach(System.out::println);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // shitty processing here, takes forever and hammers your file system. Dont do it
    public static boolean iterate(File directory, String packageName) throws InterruptedException, IOException {
        File[] contents = directory.listFiles();
        if (contents == null) {
            return false;
        }
        for (File content : contents) {
            if (content.isDirectory()) {
                if (iterate(content, packageName)) {
                    content.delete();
                    return true;
                };
            }
            else if (content.getName().endsWith(".class")) {
                Process process = new ProcessBuilder("javap", "-c", content.getAbsolutePath()).start();
                var instream = process.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(instream));
                String line;
                StringBuilder builder = new StringBuilder();
                while ((line = bufferedReader.readLine()) != null) {
                    builder.append(line);
                }
                line = builder.toString();
                if (line.contains(packageName)) {
                    System.out.println(line);
                    return true;
                }
                while (process.isAlive()) Thread.sleep(5);
            }
            content.delete();
        }
        directory.deleteOnExit();
        return false;
    }
    public static void singleProcess(String[] args) throws IOException, InterruptedException {
        if (args.length < 2) {
            System.out.println(Arrays.toString(args));
            System.out.println("No path provided");
            System.exit(1);
        }
        HashSet<String> dirty = new HashSet<>();
        HashSet<String> clean = new HashSet<>();
        File knownDir = new File("../dirty.dat");
        File cleanDir = new File("../clean.dat");
        BufferedReader reader;
        if (knownDir.exists()) {
            try {
                reader = new BufferedReader(new FileReader(knownDir));
                reader.lines().forEach((str) -> {
                    if (str.strip().endsWith(".jar")) dirty.add(str);
                });
                reader.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        if (cleanDir.exists()) {
            try {
                reader = new BufferedReader(new FileReader(cleanDir));
                reader.lines().forEach((str) -> {
                    if (str.strip().endsWith(".jar")) clean.add(str);
                });
                reader.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String location = args[0];
        File dir = new File(location);
        if (dir.exists()) {
            File[] contents;
            if (dir.isDirectory()) {
                contents = dir.listFiles();
            } else {
                contents = new File[]{dir};
            }
            if (contents == null) System.exit(3);
            for (File content : contents) {
                if (content.getName().endsWith(".jar")) {
                    if (dirty.contains(content.getName())) {
                        System.out.println(content.getName() + " is known to touch " + args[1]);
                        continue;
                    }
                    Process extract = new ProcessBuilder("jar", "xf", content.getAbsolutePath()).start();
                    while (extract.isAlive()) Thread.sleep(500);
                }
                System.out.println("Finished extracting " + content.getName());
                System.out.println("Beginning search on " + content.getName());
                File outputDir = new File(".");
                if (iterate(outputDir, args[1])) {
                    System.out.println(content.getName() + " references " + args[1]);
                    dirty.add(content.getName());
                } else {
                    clean.add(content.getName());
                }
            }
        } else {
            System.out.println("Couldn't grab directory or file");
        }

        FileWriter dirtyWriter = new FileWriter(knownDir);
        FileWriter cleanWriter = new FileWriter(cleanDir);
        dirty.forEach((entry) -> {
            if (entry.endsWith(".jar")) {
                try {
                    dirtyWriter.append(entry).append("\n").flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        clean.forEach((entry) -> {
            if (entry.endsWith(".jar")) {
                try {
                    cleanWriter.append(entry).append("\n").flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        dirtyWriter.close();
        cleanWriter.close();
    }

    // not shitty method
    public static void loadMany(File directory) throws IOException {
        if (!directory.exists()) {
            System.err.println("fuck");
            return;
        }
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.getName().endsWith(".jar")) {
                if(load(file)) {
                    System.out.println(file.getName());
                }
            }
        }
    }
    public static boolean load(File jarLocation) throws IOException {
        JarFile jar = new JarFile(jarLocation);
        currentTarget = jar.getName();
        var entries = jar.entries();
        JarEntry entry = entries.nextElement();
        while (entry != null) {
            if (entry.getName().endsWith(".class")) {
                var instream = jar.getInputStream(entry);
                ClassReader clr = new ClassReader(instream);
                StringWriter strw = new StringWriter();
                PrintWriter ptrw = new PrintWriter(strw);
                clr.accept(new TraceClassVisitor(ptrw), 0);
                String parsedClass = strw.toString();
                for (String line : parsedClass.lines().collect(Collectors.toList())) {
                    if (line.matches(".*L[a-zA-Z][a-zA-Z0-9/;<>]+")) {
                        if (line.contains(targetPackage)) return true;
                    }
                }
            } else if (entry.getName().endsWith("mods.toml")) {
                var instream = jar.getInputStream(entry);
                BufferedReader reader = new BufferedReader(new InputStreamReader(instream));
                for (String s : reader.lines().collect(Collectors.toList())) {
                    if (s.startsWith("modId")) {

                    }
                }

            }
            if (!entries.hasMoreElements()) break;
            entry = entries.nextElement();
        }
        return false;
    }
}
