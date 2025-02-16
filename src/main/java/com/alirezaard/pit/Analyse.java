package com.alirezaard.pit;

import soot.SootField;
import soot.SootMethod;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.methodSummary.data.provider.LazySummaryProvider;
import soot.jimple.infoflow.methodSummary.taintWrappers.SummaryTaintWrapper;
import soot.jimple.infoflow.results.DataFlowResult;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.cfg.InfoflowCFG;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

import static soot.jimple.infoflow.InfoflowConfiguration.AliasingAlgorithm.FlowSensitive;
import static soot.jimple.infoflow.InfoflowConfiguration.CodeEliminationMode.NoCodeElimination;
import static soot.jimple.infoflow.InfoflowConfiguration.ImplicitFlowMode.AllImplicitFlows;
import static soot.jimple.infoflow.InfoflowConfiguration.PathReconstructionMode.Fast;

public class Analyse {
    public static String outputFolder;
    public static String resultFolder;
    public static String androidJars;


    public static InfoflowCFG infoflowCFG;

    public static void main(String[] args) throws Exception {

        outputFolder = args[0];
        resultFolder = args[1];
        String mode = args[2];
        String time = args[3];
        String apkPath = args[4];
        String name = args[5];
        androidJars = args[6];

        List<String> signatures = new ArrayList<>();
        if (mode.equals("DB")){
            signatures = loadDataFromTxt("DB.txt");
        } else if (mode.equals("File")) {
            signatures = loadDataFromTxt("File.txt");
        } else if (mode.equals("Log")) {
            signatures = loadDataFromTxt("Log.txt");
        } else if (mode.equals("Net")) {
            signatures = loadDataFromTxt("Net.txt");
        } else if (mode.equals("Heavy")) {
            signatures = loadDataFromTxt("ViewSinkBig.txt");
        } else if (mode.equals("Light")) {
            signatures = loadDataFromTxt("ViewSinkShort.txt");
        }

        FileWriter fileWriter = new FileWriter("PITSignatures.txt");
        for(String sig : signatures){
            fileWriter.write(sig+"\n");
        }
        fileWriter.close();
        Long time1 = Long.parseLong(time);

        JADX jadx = new JADX(apkPath);
        SetupApplication setupApplication = setupApplication(apkPath, time1);
        InfoflowResults results = setupApplication.runInfoflow("PITSignatures.txt");
        infoflowCFG = new InfoflowCFG();

        List<String> thirdParties = loadDataFromTxt("ThirdParties.txt");
        Set<String> resultForShow = new HashSet<>();

        try {
            for (DataFlowResult result : results.getResultSet()) {
                try {
                    ResultSourceInfo source = result.getSource();
                    ResultSinkInfo sink = result.getSink();

                    View view = new View(source.getStmt(), infoflowCFG, jadx);
                    view.runAnalyze();

                    Set<SootField> soot_field = view.sootFields;
                    Set<String> viewIDs = view.viewIDs;

                    Stmt[] paths = source.getPath();
                    Set<String> parties = new HashSet<>();
                    String thirdPartySituation = "No_Path_Between_Source_And_Sink";
                    if (paths != null) {
                        for (Stmt path : paths) {
                            if (path.containsInvokeExpr()) {
                                InvokeExpr invokeExpr = path.getInvokeExpr();
                                SootMethod method = invokeExpr.getMethod();
                                for (String party : thirdParties) {
                                    if (method.getSignature().contains(party)) {
                                        parties.add(party);
                                    }
                                }
                            }
                        }
                        if (parties.isEmpty()) {
                            thirdPartySituation = "First_Party_Only";
                        } else {
                            thirdPartySituation = "Third_Party:";
                            for (String p : parties) {
                                thirdPartySituation = thirdPartySituation + p + ",";
                            }
                        }
                    }

                    String sinkType = checkerSink(sink.getStmt().getInvokeExpr().getMethod().getSignature());
                    String color = "black";
                    if (thirdPartySituation.equals("First_Party_Only")) {
                        color = "seagreen";
                    } else {
                        color = "red";
                    }

                    FileWriter FW = new FileWriter(resultFolder + "/" + name + "_result.txt", true);
                    FW.write("Source: " + source + "\n");
                    FW.write("Sink: " + sink + "\n");

                    if (!soot_field.isEmpty() || !viewIDs.isEmpty()) {
                        for (SootField A : soot_field) {
                            FW.write("SootField: " + A + "\n");
                            resultForShow.add(checker(A.getName()) + "_" + color + "_" + sinkType);
                        }
                        for (String A : viewIDs) {
                            FW.write("viewIDs: " + A + "\n");
                            resultForShow.add(checker(A) + "_" + color + "_" + sinkType);
                        }
                        FW.write("Party: " + thirdPartySituation + "\n");
                        FW.write("- - - - - - - - - - - - - - - - - - - - - - - - - - \n");
                        FW.close();
                    } else {
                        //Nothing, Later, I will get a full result report
                    }

                } catch (RuntimeException e) {
                    System.out.println("Exception " + name + "\n" + e);
                    moveAPK(apkPath, outputFolder);
                } catch (Exception e) {
                    System.out.println("Exception " + name + "\n" + e);
                    moveAPK(apkPath, outputFolder);
                } catch (Error e) {
                    System.out.println("Exception " + name + "\n" + e);
                    moveAPK(apkPath, outputFolder);
                }
            }

        } catch (RuntimeException e) {
            System.out.println("Exception " + name + "\n" + e);
            moveAPK(apkPath, outputFolder);
        }
        if (results.getResultSet() != null) {
            if (!results.getResultSet().isEmpty()) {
                String dotCode = dotCode(resultForShow, name);
                FileWriter FW = new FileWriter(resultFolder + "/" + name + "_dotCode.txt", true);
                FW.write(dotCode);
                FW.close();
               // generatePNG(dotCode, resultFolder + "/" + name + ".png");
            }
        }

    }

    public static void generatePNG(String dotString, String outputPath) {
        ProcessBuilder processBuilder = new ProcessBuilder("dot", "-Tpng");
        processBuilder.redirectOutput(new File(outputPath));
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(dotString.getBytes());
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("PNG generated successfully at: " + outputPath);
            } else {
                System.err.println("Graphviz execution error. Exit code: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        }
    }

    public static String dotCode(Set<String> dataList, String name) {

        Set<String> PI = new HashSet<>();
        Set<String> destinations = new HashSet<>();

        for (String data : dataList) {
            String[] splitter = data.split("_");
            PI.add(splitter[0]);
            destinations.add(splitter[2]);
        }
        String dotCode = "digraph G {\nnode [fixedsize=true, width=1.2, height=1];\n" +
                "graph [penwidth=1.2, color=black, margin=0.5, nodesep=1.0, ranksep=1.0];\n" +
                "subgraph cluster_G {\n" + "label=\"" + name + "\";\n" + "color=black;\nstyle=solid;\npenwidth=1;\nmargin= 5;\n";

        for (String pi : PI) {
            dotCode = dotCode + pi + " [label=\"" + pi + "\", shape=square, style=filled, fillcolor=white, fontcolor=black];\n";
        }
        for (String ds : destinations) {
            dotCode = dotCode + ds + " [label=\"" + ds + "\", shape=circle, style=filled, fillcolor=white, fontcolor=black];\n";
        }

        for (String data : dataList) {
            String[] splitter = data.split("_");
            dotCode = dotCode + splitter[0] + " -> " + splitter[2] + " [color=" + splitter[1] + ", penwidth=2];\n";
        }

        dotCode = dotCode + "}\n}";

        return dotCode;
    }


    private static String checker(String input) {
        input = input.trim().toLowerCase();
        if (input.equals("name") || input.equals("fname") ||
                ((input.contains("first") || input.contains("given")) & input.contains("name"))) {
            return "FirstName";
        } else if ((input.contains("name") & (input.contains("last") || input.contains("family"))) ||
                input.equals("lname") || input.equals("surname")) {
            return "LastName";
        } else if (input.contains("user") & input.contains("name")) {
            return "UserName";
        }  else if (input.contains("phone") || input.contains("mobile")) {
            return "Phone";
        } else if (input.contains("address") || input.contains("country") ||
                input.contains("city") || input.contains("state")) {
            return "Phone";
        } else if (((input.contains("postal") || input.contains("zip")) & input.contains("cod")) ||
                input.contains("zip")) {
            return "Phone";
        } else if (input.contains("gender") || input.contains("male") || input.contains("female") || input.contains("sex")) {
            return "Gender";
        } else if (input.contains("email")) {
            return "Email";
        }
        else if (((input.contains("age")) &
                !(input.contains("image") || input.contains("page") || input.contains("message") || input.contains("language")
                        || input.contains("centage") || input.contains("advantage") || input.contains("stage") || input.contains("average")
                        || input.contains("usage") || input.contains("mileage") || input.contains("agency") || input.contains("package")
                        || input.contains("manage") || input.contains("shortage") || input.contains("agent") || input.contains("mortgage")))
                || input.contains("birth")) {
            return "Age";
        } else if (input.contains("weight") || input.contains("kilogram") || input.contains("pound")) {
            return "Weight";
        } else if (input.contains("height") || input.contains("feet") || input.contains("inch")) {
            return "Height";
        } else if (input.contains("diabet") || input.contains("hypnotic") || input.contains("leczenie")
                || input.contains("preganc") || input.contains("period") || input.contains("ovulat") || input.contains("medical") ||
                input.contains("surger") || input.contains("allerg") ||
                (input.contains("history") & (input.contains("patient") || input.contains("past")))) {
            return "MedicalHistory";
        } else if (input.contains("medicatio") || input.contains("prescription") || input.contains("dos") || input.contains("tablet")
                || input.contains("pill")) {
            return "Medication";
        } else if (input.contains("blood")) {
            return "BloodRelated";
        } else if (input.contains("stress") || input.contains("anxiety") || input.contains("depres") ||
                input.contains("fear") || input.contains("marri") || input.contains("fell")
                || input.contains("panic")) {
            return "MentalHealth";
        } else if (input.contains("smok") || input.contains("alcoho")) {
            return "SmokeAlcohol";
        } else if (input.contains("SSN") || (input.contains("social") & input.contains("secu")) || input.contains("codemelli")) {
            return "SSN";
        } else if (input.contains("card") || input.contains("cvv")) {
            return "CardInformation";
        } else if (input.contains("password")) {
            return "Password";
        } else if (input.contains("bmi") & !(input.contains("submit"))) {
            return "BMI";
        }
        else if (input.contains("location")) {
            return "Location";
        } else {
//            if (input.contains("_")) {
//                input = input.replace("_", "0");
//            }
//            if (input.length() > 1) {
//                return input;
//            }
            return "NoCategory";
        }
    }

    private static String checkerSink(String input) {
        String result = "Unknown";
        if (input.contains("<okhttp3") || input.contains("<net") || input.contains("<android.content.ContentResolver") ||
                input.contains("<net.sqlcipher") || input.contains("<android.net") || input.contains("<com.google.firebase")
                || input.contains("<org.springframework") || input.contains("<java.net") || input.contains("network")) {
            result = "Network";
        } else if (input.contains("<android.util.Log")) {
            result = "Log";
        } else if (input.contains("<android.database") || input.contains("<androidx.room.Dao") ||
                input.contains("<com.google.firebase.database") || input.contains("<io.realm.Realm") ||
                input.contains("<android.content.SharedPreference")) {
            result = "DataBase";
        } else if (input.contains("<java.io") || input.contains("<java.nio")) {
            result = "File";
        } else if (input.contains("<android.content.Intent")) {
            result = "Intent";
        } else if (input.contains("<android.telephony")) {
            result = "SMS";
        } else if (input.contains("<android.content.Context") || input.contains("Intent")) {
            result = "Intent";
        } else if (input.contains("<android.os")) {
            result = "Bundle";
        }
        return result;
    }


    public static SetupApplication setupApplication(String apkFile, Long time) throws URISyntaxException, IOException {
        SetupApplication setupApplication = new SetupApplication(androidJars, apkFile);
        InfoflowAndroidConfiguration config = setupApplication.getConfig();
        config.setMergeDexFiles(true);
        config.setAliasingAlgorithm(FlowSensitive);
        config.setCodeEliminationMode(NoCodeElimination);
        config.getSolverConfiguration().setDataFlowSolver(InfoflowConfiguration.DataFlowSolver.ContextFlowSensitive);
        config.getSolverConfiguration().setSparsePropagationStrategy(InfoflowConfiguration.SparsePropagationStrategy.Precise);
        //config.setDataFlowDirection(InfoflowConfiguration.DataFlowDirection.Backwards);
        config.setImplicitFlowMode(AllImplicitFlows);
        config.getPathConfiguration().setPathReconstructionMode(Fast);
        config.getPathConfiguration().setPathBuildingAlgorithm(InfoflowConfiguration.PathBuildingAlgorithm.ContextInsensitive);
        config.setMemoryThreshold(1.0d);
        config.setDataFlowTimeout(time);
        config.getPathConfiguration().setPathReconstructionTimeout(time);
        config.getPathConfiguration().setMaxPathLength(100);
        config.setLogSourcesAndSinks(true);
        setupApplication.setTaintWrapper(new SummaryTaintWrapper(new LazySummaryProvider("summariesManual")));
        return setupApplication;
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

    public static List<String> loadDataFromTxt(String where) throws Exception {
        InputStream inputStream = Analyse.class.getResourceAsStream("/" + where);
        if (inputStream == null) {
            throw new IllegalArgumentException("File not found in resources!!!");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toList());
        }
    }
}