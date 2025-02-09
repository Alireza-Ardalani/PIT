package com.alirezaard.pit;

import org.xmlpull.v1.XmlPullParserException;
import soot.SootField;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.config.IInfoflowConfig;
import soot.jimple.infoflow.results.InfoflowResults;
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
        //System.out.println(jadx.getManifestCode());

        SetupApplication setupApplication = setupApplication(apkPath);
        InfoflowResults results = setupApplication.runInfoflow(Signatures);
        Set<Stmt> sources = setupApplication.getCollectedSources();
        infoflowCFG = new InfoflowCFG();
        //CallGraph callGraph = Scene.v().getCallGraph();
        //System.out.println(callGraph.size());

        FileWriter FW = new FileWriter("/Users/alirezaardalani/Desktop/Shokhm.txt",true);
        for (Stmt sourceStmt : sources) {
            View V = new View(sourceStmt, infoflowCFG, jadx);
            V.runAnalyze();

            System.out.println("Method--> " + infoflowCFG.getMethodOf(sourceStmt));
            System.out.println("Sources--> " + sourceStmt);
            System.out.println("SootField--> " + V.sootFields);
            System.out.println("findView--> " + V.findViewByIdStmts);
            System.out.println("IDNumber--> " + V.viewIDNumbers);
            System.out.println("ID--> " + V.viewIDs);
            // method(sourceStmt);
            System.out.println("----------NNNNNNNNEEEEEEEEXXXXXXTTTTT----------");

            for(SootField i : V.sootFields){
                FW.write("SootFieldClass: " + i.getDeclaringClass()+"\n");
            }
            for(SootField i : V.sootFields){
                FW.write("SootField: " + i.getName()+"\n");
            }
            for(String i : V.viewIDs){
                FW.write("ViewID: " + i+"\n");
            }

            FW.write("------------------------"+"\n");
        }
        FW.close();
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
        config.setCodeEliminationMode(NoCodeElimination);
        config.getSolverConfiguration().setDataFlowSolver(InfoflowConfiguration.DataFlowSolver.ContextFlowSensitive);
        config.getSolverConfiguration().setSparsePropagationStrategy(InfoflowConfiguration.SparsePropagationStrategy.Precise);
        config.setImplicitFlowMode(AllImplicitFlows);
        config.getPathConfiguration().setPathBuildingAlgorithm(ContextInsensitiveSourceFinder);
        config.setMemoryThreshold(1.0d);
        config.setDataFlowTimeout(5);
        config.getPathConfiguration().setMaxPathLength(100);
        config.setLogSourcesAndSinks(true);
        config.getAccessPathConfiguration().setAccessPathLength(10);
        return setupApplication;
    }
}