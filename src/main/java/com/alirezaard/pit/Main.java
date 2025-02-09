package com.alirezaard.pit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {
    public static String inputFolder = "/Users/alirezaardalani/Desktop/T1";
    public static String outputFolder = "/Users/alirezaardalani/Desktop/T2";
    public static String resultFolder = "/Users/alirezaardalani/Desktop/T3";
    public static String Signatures = "/Users/alirezaardalani/Desktop/ViewSink.txt";
    public static String androidJars = "/Users/alirezaardalani/Library/Android/sdk/platforms";

    public static void main(String[] args) throws IOException, InterruptedException {
//        inputFolder = args[0];
//        outputFolder = args[1];
//        resultFolder = args[2];
//        IOSignatures = args[3];
//        jarFile = args[4];



        List<String> fileNames = findApkFiles(inputFolder);
        for (String name : fileNames) {

            System.out.println(name);
            String apkPath = inputFolder + "/" + name;
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java", "-cp", System.getProperty("java.class.path"), "com.alirezaard.pit.Analyse",
                    outputFolder,resultFolder,Signatures,apkPath,name,androidJars);
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            Process process = processBuilder.start();
            boolean finished = process.waitFor(1050, TimeUnit.SECONDS);
            if (!finished) {
                System.out.println("Analysis took too long, terminating...");
                process.destroy();  // Graceful termination
                if (process.isAlive()) {
                    process.destroyForcibly(); // Forceful termination if still alive
                }

               // moveAPK(apkPath,outputFolder);
            }
        }
    }

    public static List<String> findApkFiles(String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            System.out.println("Invalid directory path");
            return null;
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".apk"));
        List<String> filesName = new ArrayList<String>();
        if (files != null) {
            for (File file : files) {
                filesName.add(file.getName());

            }
        }
        return filesName;
    }
    private static void moveAPK(String apkPath, String finishFolder) {
        File sourceFile = new File(apkPath);
        File destinationDirectory = new File(finishFolder);

        if (!sourceFile.exists() || !destinationDirectory.exists()) {
            System.err.println("Source file or destination directory does not exist.");
        }
        try {
            Path sourcePath = sourceFile.toPath();
            Path destinationPath = new File(destinationDirectory, sourceFile.getName()).toPath();

            Files.move(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("File moved successfully!");
        } catch (IOException e) {
            System.err.println("Error moving file: " + e.getMessage());
        }
    }
}

