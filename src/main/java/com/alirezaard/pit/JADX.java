package com.alirezaard.pit;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;

import java.io.File;
import java.util.List;

public class JADX {
    private final JadxDecompiler jadx;

    public JADX(String apkPath) {
        JadxArgs jadxArgs = new JadxArgs();
        jadxArgs.setInputFile(new File(apkPath));
        this.jadx = new JadxDecompiler(jadxArgs);
        jadxArgs.setShowInconsistentCode(true);
        jadxArgs.setDebugInfo(true);
        jadxArgs.setDeobfuscationOn(true);
        jadx.load();
    }
    public String findElementID(String input){
        String output = "";
        int ID = 0;
        try {
            ID = Integer.parseInt(input);
        }
        catch (Exception e ){
        }
        if(ID==0){
            return "NoElementFound";
        }
        String hexNumber = Integer.toHexString(ID);
        String code = findIDinRClass();
        String[] codeArray  = code.split("\n");
        for(String lineOfCode : codeArray){
            if(lineOfCode.contains(hexNumber)){
                int first = lineOfCode.indexOf("public static final int ");
                int last =  lineOfCode.indexOf(" =");
                output =  lineOfCode.substring(first+24,last);
            }
        }
        //new
        if(output.equals("")){
            // System.out.println("Amejende1");
            List<JavaClass> classes = jadx.getClasses();
            for (JavaClass cls : classes) {
                String className  = cls.getFullName();
                String[] splitter =  className.split("\\.");
                for(String split : splitter){
                    if(split.equals("R")){
                        String code1 = cls.getCode();
                        String[] codeArray1  = code1.split("\n");
                        for(String lineOfCode : codeArray1){
                            if(lineOfCode.contains(hexNumber)){
                                int first = lineOfCode.indexOf("public static final int ");
                                int last =  lineOfCode.indexOf(" =");
                                output =  lineOfCode.substring(first+24,last);
                            }
                        }
                    }
                }
            }

        }
        if(output.equals("")){
            List<JavaClass> classes = jadx.getClasses();
            for (JavaClass cls : classes) {
                String className  = cls.getFullName();
                if(className.contains("R")){
                    String code1 = cls.getCode();
                    String[] codeArray1  = code1.split("\n");
                    for(String lineOfCode : codeArray1){
                        if(lineOfCode.contains(hexNumber) || lineOfCode.contains(String.valueOf(ID))){
                            int first = lineOfCode.indexOf("public static final int ");
                            int last =  lineOfCode.indexOf(" =");
                            if(first!=-1 && last!=-1 ){
                                output =  lineOfCode.substring(first+24,last);
                            }
                        }
                    }
                }
            }
        }
        return output;
    }
    public String findIDinRClass(){
        String output = "";
        List<JavaClass> classes = jadx.getClasses();
        for (JavaClass cls : classes) {
            String className  = cls.getFullName();
            String[] splitter =  className.split("\\.");
            for(String split : splitter){
                if(split.equals("R")){
                    String code = cls.getCode();
                    int startPosition = code.indexOf("static final class id {");
                    if(startPosition != -1){
                        code = code.substring(startPosition);
                        int endPosition = code.indexOf("}");
                        code = code.substring(0,endPosition+1);
                        output = output + code;
                    }
                }
            }
        }
        return output;
    }
}
