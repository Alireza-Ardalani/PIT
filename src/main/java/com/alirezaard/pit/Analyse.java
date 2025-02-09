package com.alirezaard.pit;

import org.xmlpull.v1.XmlPullParserException;
import soot.SootField;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.config.IInfoflowConfig;
import soot.jimple.infoflow.results.DataFlowResult;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.cfg.InfoflowCFG;
import soot.options.Options;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static soot.jimple.infoflow.InfoflowConfiguration.AliasingAlgorithm.FlowSensitive;
import static soot.jimple.infoflow.InfoflowConfiguration.CodeEliminationMode.NoCodeElimination;
import static soot.jimple.infoflow.InfoflowConfiguration.ImplicitFlowMode.AllImplicitFlows;
import static soot.jimple.infoflow.InfoflowConfiguration.PathBuildingAlgorithm.ContextInsensitiveSourceFinder;
import static soot.jimple.infoflow.InfoflowConfiguration.PathBuildingAlgorithm.ContextSensitive;
import static soot.jimple.infoflow.InfoflowConfiguration.PathReconstructionMode.Precise;

public class Analyse {
    //public static String inputFolder = "/Users/alirezaardalani/Desktop/T1";
    public static String outputFolder = "/Users/alirezaardalani/Desktop/T2";
    public static String resultFolder = "/Users/alirezaardalani/Desktop/T3";
    //public static String Signatures = "/Users/alirezaardalani/Desktop/SignatureLog.txt";
    public static String Signatures = "/Users/alirezaardalani/Desktop/ViewSink.txt";
    public static String androidJars = "/Users/alirezaardalani/Desktop/platforms";

    public static InfoflowCFG infoflowCFG;

    public static void main(String[] args) throws XmlPullParserException, IOException, URISyntaxException {

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
        //Set<Stmt> sources = setupApplication.getCollectedSources();

        for (DataFlowResult result :  results.getResultSet()){
            ResultSourceInfo source = result.getSource();
            ResultSinkInfo sink = result.getSink();
            View view = new View(source.getStmt(), infoflowCFG, jadx);
            view.runAnalyze();

            Set<SootField> soot_field = view.sootFields;
            Set<String> viewIDs = view.viewIDs;
            Stmt[] paths = source.getPath();

            FileWriter FW = new FileWriter(outputFolder+"/"+name+".txt",true);
            if (!soot_field.isEmpty() ||  !viewIDs.isEmpty()){
               if (paths.length > 0) {
                   for(SootField A : soot_field){
                       FW.write("SootField: " + A + "\n");
                   }
                   for(String A : viewIDs){
                       FW.write("viewIDs: " + A + "\n");
                   }
                   for (var path :  paths){
                       FW.write("path: " + path + "\n");
                   }
               }
               else {
                   //No-path!
                   for(SootField A : soot_field){
                       FW.write("SootField: " + A + "\n");
                   }
                   for(String A : viewIDs){
                       FW.write("viewIDs: " + A + "\n");
                   }
               }
               FW.close();
            }

            //System.out.println("length---> " +);
        }


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

    public static SetupApplication setupApplication(String apkFile) {
        SetupApplication setupApplication = new SetupApplication(androidJars, apkFile);
        InfoflowAndroidConfiguration config = setupApplication.getConfig();

        IInfoflowConfig configSoot = new IInfoflowConfig() {
            @Override
            public void setSootOptions(Options options, InfoflowConfiguration config) {
                Options.v().set_whole_program(true);
                Options.v().set_allow_phantom_refs(false);
                Options.v().set_no_bodies_for_excluded(true);
                Options.v().set_process_multiple_dex(true);
                Options.v().set_include_all(true);
            }
        };
        setupApplication.setSootConfig(configSoot);

        config.setMergeDexFiles(true);
        config.setAliasingAlgorithm(FlowSensitive);
        config.getSolverConfiguration().setDataFlowSolver(InfoflowConfiguration.DataFlowSolver.ContextFlowSensitive);
        //config.setCallgraphAlgorithm(InfoflowConfiguration.CallgraphAlgorithm.CHA);
        config.getSolverConfiguration().setSparsePropagationStrategy(InfoflowConfiguration.SparsePropagationStrategy.Precise);
        config.setDataFlowDirection(InfoflowConfiguration.DataFlowDirection.Backwards);
        config.setImplicitFlowMode(AllImplicitFlows);
        config.getPathConfiguration().setPathReconstructionMode(Precise);
        config.getPathConfiguration().setPathBuildingAlgorithm(ContextSensitive);
        config.setMemoryThreshold(1.0d);
        config.setDataFlowTimeout(1000);
        config.getPathConfiguration().setPathReconstructionTimeout(500);
        config.getPathConfiguration().setPathReconstructionBatchSize(10);
        config.getPathConfiguration().setMaxPathLength(100);
        config.setLogSourcesAndSinks(true);
        config.getAccessPathConfiguration().setAccessPathLength(5);
        config.getAccessPathConfiguration().setUseRecursiveAccessPaths(false);
        config.getAccessPathConfiguration().setUseThisChainReduction(false);

        return setupApplication;
    }
}