package com.alirezaard.pit;

import org.xmlpull.v1.XmlPullParserException;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.config.IInfoflowConfig;
import soot.jimple.infoflow.methodSummary.data.provider.EagerSummaryProvider;
import soot.jimple.infoflow.methodSummary.data.provider.LazySummaryProvider;
import soot.jimple.infoflow.methodSummary.taintWrappers.SummaryTaintWrapper;
import soot.jimple.infoflow.results.DataFlowResult;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.cfg.InfoflowCFG;
import soot.options.Options;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static soot.jimple.infoflow.InfoflowConfiguration.AliasingAlgorithm.FlowSensitive;
import static soot.jimple.infoflow.InfoflowConfiguration.CodeEliminationMode.NoCodeElimination;
import static soot.jimple.infoflow.InfoflowConfiguration.ImplicitFlowMode.AllImplicitFlows;
import static soot.jimple.infoflow.InfoflowConfiguration.PathBuildingAlgorithm.ContextInsensitiveSourceFinder;
import static soot.jimple.infoflow.InfoflowConfiguration.PathBuildingAlgorithm.ContextSensitive;
import static soot.jimple.infoflow.InfoflowConfiguration.PathReconstructionMode.Fast;
import static soot.jimple.infoflow.InfoflowConfiguration.PathReconstructionMode.Precise;

public class Analyse {
    //public static String inputFolder = "/Users/alirezaardalani/Desktop/T1";
    public static String outputFolder = "/Users/alirezaardalani/Desktop/T2";
    public static String resultFolder = "/Users/alirezaardalani/Desktop/T3";
    //public static String Signatures = "/Users/alirezaardalani/Desktop/SignatureLog.txt";
    public static String Signatures = "/Users/alirezaardalani/Desktop/ViewSink.txt";
    public static String androidJars = "/Users/alirezaardalani/Desktop/platforms";

    public static InfoflowCFG infoflowCFG;

    public static void main(String[] args) throws Exception {

        String outputFolder = args[0];
        String resultFolder = args[1];
        String Signatures = args[2];
        String apkPath = args[3];
        String name = args[4];
        String androidJars = args[5];
        JADX jadx = new JADX(apkPath);
        SetupApplication setupApplication = setupApplication(apkPath);
        InfoflowResults results = setupApplication.runInfoflow(Signatures);
        infoflowCFG = new InfoflowCFG();

        List<String> thirdParties = loadDataFromTxt("ThirdParties.txt");
        System.out.println(thirdParties);
        try {
            for (DataFlowResult result :  results.getResultSet()){
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
                    for (Stmt path :  paths){
                        if(path.containsInvokeExpr()){
                            InvokeExpr invokeExpr  = path.getInvokeExpr();
                            SootMethod method = invokeExpr.getMethod();
                            for(String party: thirdParties){
                                if(method.toString().contains(party)){
                                    parties.add(party);
                                }
                            }
                        }
                    }
                    if(parties.isEmpty()){
                        thirdPartySituation = "First_Party_Only";
                    }
                    else {
                        thirdPartySituation = "";
                        for(String p : parties){
                            thirdPartySituation = thirdPartySituation + p + "," ;
                        }
                    }
                }

                FileWriter FW = new FileWriter(resultFolder+"/"+name+".txt",true);
                if (!soot_field.isEmpty() ||  !viewIDs.isEmpty()){
                        for(SootField A : soot_field){
                            FW.write("SootField: " + A + "\n");
                        }
                        for(String A : viewIDs){
                            FW.write("viewIDs: " + A + "\n");
                        }
                        FW.write("Party: " + thirdPartySituation + "\n");
                    FW.write("- - - - - - - - - - - - - - - - - - - - - - - - - - \n");
                    FW.close();
                }
                else {
                    //Nothing, Later, I will get a full result report
                }
            }

        }
        catch (RuntimeException e){
            //moveAPK(apkPath,outputFolder);
            System.out.println(e);
        }
       // moveAPK(apkPath,outputFolder);
    }





    private static String checker(String input) {
        input = input.trim().toLowerCase();
        if (input.equals("name") || input.equals("fname") ||
                ((input.contains("first") || input.contains("given")) & input.contains("name"))) {
            return "First_Name";
        } else if ((input.contains("name") & (input.contains("last") || input.contains("family"))) ||
                input.equals("lname") || input.equals("surname")) {
            return "Last_Name";
        } else if (input.contains("email")) {
            return "Email";
        } else if (input.contains("phone") || input.contains("mobile")) {
            return "Phone";
        } else if (input.contains("address") || input.contains("country") ||
                input.contains("city") || input.contains("state")) {
            return "Phone";
        } else if (((input.contains("postal") || input.contains("zip")) & input.contains("cod")) ||
                input.contains("zip")) {
            return "Phone";
        } else if (input.contains("gender") || input.contains("male") || input.contains("female") || input.contains("sex")) {
            return "Gender";
        } else if (((input.contains("age")) &
                !(input.contains("image") || input.contains("page") || input.contains("message") || input.contains("language")
                        || input.contains("centage") ||input.contains("advantage") || input.contains("stage") || input.contains("average")
                        || input.contains("usage") || input.contains("mileage") ||input.contains("agency") || input.contains("package")
                        || input.contains("manage") || input.contains("shortage")|| input.contains("agent") || input.contains("mortgage")))
                || input.contains("birth")) {
            return "Age";
        } else if (input.contains("weight") || input.contains("kilogram") || input.contains("pound")) {
            return "Weight";
        } else if (input.contains("height") || input.contains("feet") || input.contains("inch")) {
            return "Height";
        }  else if (input.contains("diabet") || input.contains("hypnotic") || input.contains("leczenie")
                || input.contains("preganc") || input.contains("period") || input.contains("ovulat") || input.contains("medical") ||
                input.contains("surger") || input.contains("allerg") ||
                (input.contains("history") & (input.contains("patient") || input.contains("past")))) {
            return "Medical_History";
        } else if (input.contains("medicatio") || input.contains("prescription") || input.contains("dos") || input.contains("tablet")
                || input.contains("pill")) {
            return "Medication";
        } else if (input.contains("blood")) {
            return "Blood-Related";
        }
        else if (input.contains("stress") || input.contains("anxiety") || input.contains("depres") ||
                input.contains("fear") || input.contains("marri") || input.contains("fell")
                || input.contains("panic")) {
            return "Mental_Health";
        } else if (input.contains("smok") || input.contains("alcoho")) {
            return "Smoke_Alcohol";
        } else if (input.contains("SSN") || (input.contains("social") & input.contains("secu")) || input.contains("codemelli")) {
            return "SSN";
        } else if (input.contains("card") || input.contains("cvv")) {
            return "Card_Information";
        }else if(input.contains("password")){
            return "Password";
        } else if (input.contains("bmi") & !(input.contains("submit"))) {
            return "BMI";
        } else {
            return "NOT_CATEGORIZED";
        }
        //return input;
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

    public static SetupApplication setupApplication(String apkFile) throws URISyntaxException, IOException {
//        IInfoflowConfig configSoot = new IInfoflowConfig() {
//            @Override
//            public void setSootOptions(Options options, InfoflowConfiguration config) {
//                Options.v().set_whole_program(true);
//                Options.v().set_allow_phantom_refs(false);
//                Options.v().set_no_bodies_for_excluded(true);
//                Options.v().set_process_multiple_dex(true);
//                Options.v().set_include_all(true);
//            }
//        };
//        setupApplication.setSootConfig(configSoot);

//        config.setMergeDexFiles(true);
//        config.setAliasingAlgorithm(FlowSensitive);
//        config.getSolverConfiguration().setDataFlowSolver(InfoflowConfiguration.DataFlowSolver.ContextFlowSensitive);
//        //config.setCallgraphAlgorithm(InfoflowConfiguration.CallgraphAlgorithm.CHA);
//        config.getSolverConfiguration().setSparsePropagationStrategy(InfoflowConfiguration.SparsePropagationStrategy.Precise);
//        config.setDataFlowDirection(InfoflowConfiguration.DataFlowDirection.Backwards);
//        config.setImplicitFlowMode(AllImplicitFlows);
//        config.getPathConfiguration().setPathReconstructionMode(Precise);
//        config.getPathConfiguration().setPathBuildingAlgorithm(ContextSensitive);
//        config.setMemoryThreshold(1.0d);
//        config.setDataFlowTimeout(1750);
//        config.getPathConfiguration().setPathReconstructionTimeout(750);
//        config.getPathConfiguration().setPathReconstructionBatchSize(10);
//        config.getPathConfiguration().setMaxPathLength(100);
//        config.setLogSourcesAndSinks(true);
//        config.getAccessPathConfiguration().setAccessPathLength(10);
////        config.getAccessPathConfiguration().setUseRecursiveAccessPaths(false);
////        config.getAccessPathConfiguration().setUseThisChainReduction(false);
//        setupApplication.setTaintWrapper(new SummaryTaintWrapper(new LazySummaryProvider("summariesManual")));

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
        config.getPathConfiguration().setPathBuildingAlgorithm(ContextSensitive);
        config.setMemoryThreshold(1.0d);
        config.setDataFlowTimeout(1750);
        config.getPathConfiguration().setPathReconstructionTimeout(1000);
        config.getPathConfiguration().setMaxPathLength(100);
        config.setLogSourcesAndSinks(true);

        setupApplication.setTaintWrapper(new SummaryTaintWrapper(new LazySummaryProvider("summariesManual")));
//        //setupApplication.setTaintWrapper(new SummaryTaintWrapper(new EagerSummaryProvider("summariesManual")));
//        config.getAccessPathConfiguration().setAccessPathLength(10);

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