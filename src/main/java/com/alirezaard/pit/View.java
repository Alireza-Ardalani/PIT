package com.alirezaard.pit;

import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.solver.cfg.InfoflowCFG;
import soot.jimple.internal.*;
import soot.tagkit.AnnotationTag;
import soot.tagkit.Tag;
import soot.tagkit.VisibilityAnnotationTag;
import soot.util.Chain;

import java.util.*;

public class View {

    Stmt stmt;

    // Make this public to private and use getter and setter instead of them!
    public Set<SootField> sootFields = new HashSet<>() ;
    public Set<Stmt> findViewByIdStmts = new HashSet<>() ;
    public Set<String> viewIDNumbers = new HashSet<>() ;
    public Set<String> viewIDs = new HashSet<>();
    JADX jadx;
    // CallGraph CallGraph;
    InfoflowCFG infoflowCFG;
    public View(Stmt inputStmt, InfoflowCFG inputInfoflowCFG, JADX inputJadx){
        this.stmt = inputStmt;
        this.infoflowCFG = inputInfoflowCFG;
        this.jadx = inputJadx;
        //this.CallGraph = inputCallGraph;
    }

    public void runAnalyze() {

        method(this.stmt);
        ViewID viewID = new ViewID(infoflowCFG);
        for(Stmt stmt: findViewByIdStmts){
            viewID.run(stmt, jadx);
        }
        sootFields.addAll(viewID.getSootFields());
        viewIDNumbers.addAll(viewID.getViewIDNumbers());
        viewIDs.addAll(viewID.getViewIDs());

    }
    private  void method(Stmt sourceStmt) {

        // Maybe the Source method be <jd$1: void onClick(android.view.View)>($r3)
        Value defValue = null;
        SootMethod method = infoflowCFG.getMethodOf(sourceStmt);
        if(sourceStmt instanceof JAssignStmt){
            JAssignStmt jAssignStmt = (JAssignStmt)sourceStmt;
            InvokeExpr invokeExpr = jAssignStmt.getInvokeExpr();
            if(invokeExpr instanceof JVirtualInvokeExpr){
                JVirtualInvokeExpr jVirtualInvokeExpr = (JVirtualInvokeExpr)invokeExpr;
                defValue = jVirtualInvokeExpr.getBase();
            }
            //else {System.err.println("Error, Bad Cast Handling: It is not JVirtualInvokeExpr");}
        }
        else if(sourceStmt instanceof JInvokeStmt){
            JInvokeStmt jInvokeStmt = (JInvokeStmt) sourceStmt;
            InvokeExpr invokeExpr = jInvokeStmt.getInvokeExpr();
            if(invokeExpr instanceof JVirtualInvokeExpr){
                JVirtualInvokeExpr jVirtualInvokeExpr = (JVirtualInvokeExpr)invokeExpr;
                defValue = jVirtualInvokeExpr.getBase();
            }
            //else {System.err.println("LOG: Error, Bad Cast Handling: It is not JVirtualInvokeExpr");}
        }
        //else{System.err.println("LOG: Error, Bad Cast Handling: It is not JAssignStmt");}
        backwardFlowTrack(sourceStmt,defValue,method);
    }

    private  void backwardFlowTrack(Stmt startStmt, Value targetObject, SootMethod method) {
        if (startStmt == null || targetObject == null || method == null) {
            System.out.println("LOG: Error: startStmt, targetObject, or method is null.");
            return;
        }
        if(startStmt.toString().contains("findViewById")){
            findViewByIdStmts.add(startStmt);
            return;
        }

        // System.out.println("---+++--->> " +startStmt);
        // System.out.println(">> " +method);
        // System.out.println("LOG: Starting backward tracking for: " + targetObject + " in method: " + method.getSignature());

        Body body = method.retrieveActiveBody();
        PatchingChain<Unit> units = body.getUnits();

        List<Unit> reversedUnits = new ArrayList<>(units);
        Collections.reverse(reversedUnits);

        boolean startReached = false;
        boolean originFound = false;

        for (Unit unit : reversedUnits) {
            Stmt stmt = (Stmt) unit;

            // System.out.println("-------<<<>>> " + stmt + " - " + stmt.getClass());
            if (stmt == startStmt) {
                startReached = true;
                //System.out.println("LOG: Reached the start statement: " + startStmt);
                continue;
            }

            if (!startReached) {
                continue;
            }

            if (stmt instanceof JAssignStmt) {
                JAssignStmt assignStmt = (JAssignStmt) stmt;

                if (assignStmt.getLeftOp().equivTo(targetObject)) {
                    Value rightOp = assignStmt.getRightOp();

                    if (rightOp instanceof FieldRef) {
                        SootField field = ((FieldRef) rightOp).getField();
                        // System.out.println("LOG: Origin found: class field (" + field + ")");
                        sootFields.add(field);
                        originFound = true;
                        trackFieldDefinition_(field);
                        break;
                    }

                    if (rightOp instanceof InvokeExpr) {
                        SootMethod invokedMethod = ((InvokeExpr) rightOp).getMethod();

                        // System.out.println("LOG: Origin found: return value of method (" + invokedMethod.getSignature() + ")");
                        if(invokedMethod.getSignature().contains("findViewById")){
                            findViewByIdStmts.add(stmt);
                            //   System.out.println("LOG: Origin found: Good findViewById...: " + stmt);
                            originFound = true;
                            break;
                        }

                        if (invokedMethod.isConcrete() && invokedMethod.hasActiveBody()) {
                            // System.out.println("LOG: Recursively tracking return value in method: " + invokedMethod.getSignature());
                            trackReturnValue(invokedMethod);
                        } else if (invokedMethod.getSignature().contains("findViewById")) {
                            findViewByIdStmts.add(stmt);
                            // System.out.println("LOG: Why we are hereeeeee?: Good findViewById: " + stmt);
                        } else {
                            //System.out.println("LOG: Cannot track further; method is not concrete: " + invokedMethod.getSignature());
                        }

                        originFound = true;
                        break;
                    }

                    if (rightOp instanceof IdentityRef && rightOp instanceof ParameterRef) {
                        //System.out.println("LOG: Origin found: method parameter");
                        resolveParameter(method, (ParameterRef) rightOp);
                        originFound = true;
                        break;
                    }

                    if (rightOp instanceof CastExpr) {
                        targetObject = ((CastExpr) rightOp).getOp();
                        continue;
                    }
                }
            }

            if (stmt instanceof JIdentityStmt) {
                JIdentityStmt identityStmt = (JIdentityStmt) stmt;

                if (identityStmt.getLeftOp().equivTo(targetObject)) {
                    Value rightOp = identityStmt.getRightOp();

                    if (rightOp instanceof ParameterRef) {
                        int paramIndex = ((ParameterRef) rightOp).getIndex();
                        //System.out.println("LOG: Origin found: method parameter (param" + paramIndex + ")");
                        resolveParameter(method, (ParameterRef) rightOp);
                        originFound = true;
                        break;
                    }

                    if (rightOp instanceof ThisRef) {
                        SootClass enclosingClass = method.getDeclaringClass();
                        // System.out.println("LOG: Target object corresponds to 'this' of class: " + enclosingClass);

                        for (SootField field : enclosingClass.getFields()) {
                            if (field.getType().equals(targetObject.getType())) {
                                //  System.out.println("LOG: Origin found: class field (" + field.getName() + ")");
                                originFound = true;
                                break;
                            }
                        }

                        if (!originFound) {
                            //  System.out.println("LOG: No matching field found in class: " + enclosingClass);
                        }
                        break;
                    }
                }
            }
            if(stmt instanceof JInvokeStmt){
                JInvokeStmt JInvokeStmt = (JInvokeStmt) stmt;
                InvokeExpr InvokeExpr = JInvokeStmt.getInvokeExpr();
                if(InvokeExpr instanceof JSpecialInvokeExpr){
                    JSpecialInvokeExpr JSpecialInvokeExpr = (JSpecialInvokeExpr) InvokeExpr;
                    if(JSpecialInvokeExpr.getBase().equivTo(targetObject)){
                        // System.out.println("New instantiate");
                        String classObject = "empty";
                        if(infoflowCFG.getMethodOf(stmt).getDeclaringClass().toString().equals("dummyMainClass")){
                            String[] split = infoflowCFG.getMethodOf(stmt).getSignature().toString().split(" ");
                            classObject = split[1];
                        }
                        else {
                            classObject = infoflowCFG.getMethodOf(stmt).getDeclaringClass().toString();
                        }
                        // System.out.println("New instantiate ---> " + classObject);
                        // TODO
                        break;
                    }
                }
            }
        }

        if (!originFound) {
            //  System.out.println("LOG: Could not find a valid origin for targetObject: " + targetObject);
        }

        // System.out.println("------ end of method ------");
    }


    private  void resolveParameter(SootMethod method, ParameterRef paramRef) {
        int paramIndex = paramRef.getIndex();
        // System.out.println("LOG: Resolving parameter at index: " + paramIndex);

        Collection<Unit> callSites = infoflowCFG.getCallersOf(method);

        if (callSites.isEmpty()) {
            //   System.out.println("LOG: No callers found for method: " + method.getSignature());
            extraCallFinder(method,paramIndex);
            return;
        }
        int i = 0;
        for (Unit callSite : callSites) {
            if (!(callSite instanceof Stmt)) {
                continue;
            }
            Stmt stmt = (Stmt) callSite;
            InvokeExpr invokeExpr = stmt.getInvokeExpr();
            if (invokeExpr == null) {
                continue;
            }
            if (paramIndex >= invokeExpr.getArgs().size()) {
                //System.out.println("LOG: Parameter index out of bounds for invocation at: " + stmt);
                continue;
            }

            Value argument = invokeExpr.getArg(paramIndex);
            SootMethod callerMethod = infoflowCFG.getMethodOf(stmt);

            //  System.out.println("LOG: Found invocation in caller method: " + callerMethod.getSignature());
            //  System.out.println("LOG: Passing argument: " + argument + " for tracking.");

            i++;
            //  System.out.println("Track call Site -> " + i );
            backwardFlowTrack(stmt, argument, callerMethod);
        }
    }

    private void extraCallFinder(SootMethod method, int paramIndex) {
        Chain<SootClass> classes = Scene.v().getApplicationClasses();
        for(SootClass clazz : classes){
            for(SootMethod innerMethod : clazz.getMethods()){
                try {
                    innerMethod.retrieveActiveBody();
                }
                catch (Exception e){
                        /*
                        System.out.println("Some Method Have problem!! " + e);
                        --->
                        java.lang.RuntimeException: No method source set for method
                         */
                    continue;
                }
                if(innerMethod.hasActiveBody()){
                    Body body =  innerMethod.getActiveBody();
                    PatchingChain<Unit> units = body.getUnits();
                    for (Unit unit : units) {
                        Stmt stmt = (Stmt) unit;
                        if(stmt.containsInvokeExpr() && stmt.toString().contains(method.getSignature())){
                            InvokeExpr invokeExpr = stmt.getInvokeExpr();
                            if (invokeExpr == null) {
                                continue;
                            }
                            if (paramIndex >= invokeExpr.getArgs().size()) {
                                //System.out.println("LOG: Parameter index out of bounds for invocation at: " + stmt);
                                continue;
                            }

                            Value argument = invokeExpr.getArg(paramIndex);

                            //  System.out.println("LOG: Found invocation in caller method: " + innerMethod.getSignature());
                            //  System.out.println("LOG: Passing argument: " + argument + " for tracking.");

                            //  System.out.println("Track call Site -> " + "SPECIAL");
                            backwardFlowTrack(stmt, argument, innerMethod);
                        }
                    }
                }
            }
        }
    }


    private  void trackReturnValue(SootMethod method) {
        Body body = method.retrieveActiveBody();
        PatchingChain<Unit> units = body.getUnits();
        for (Unit unit : units) {
            if (unit instanceof JReturnStmt) {
                JReturnStmt returnStmt = (JReturnStmt) unit;
                Value returnValue = returnStmt.getOp();
                //System.out.println("LOG: Tracking return value: " + returnValue + " in method: " + method.getSignature());
                backwardFlowTrack(returnStmt, returnValue, method);
            }
        }
    }

    private  void trackFieldDefinition_(SootField field) {

        String annotationID = isAnnotationBindView(field);
        if (!annotationID.equals("-1")) {
            //   System.out.println("Field " + field.getName() + " @BindView annotation-> " + annotationID );
            viewIDNumbers.add(annotationID);
            //view.setNumberID(isAnnotationBindView(field));
            return ;  // Stop tracking, since it's handled by annotation binding
        }

        SootClass clazz = field.getDeclaringClass();
        for (SootMethod method : clazz.getMethods()) {
            try {
                method.retrieveActiveBody();
            }
            catch (Exception exception) {
                System.out.println("Exception: " + exception);
                continue;
            }

            if (!method.hasActiveBody()) {
                continue;
            }
            Body body = method.getActiveBody();
            PatchingChain<Unit> units = body.getUnits();
            for(Unit unit: units){
                Stmt stmt = (Stmt) unit;
                List<ValueBox> defs = stmt.getDefBoxes();
                for(ValueBox def: defs){
                    if (def.getValue() instanceof FieldRef) {
                        //System.out.println(def);
                        SootField field1 = ((FieldRef) def.getValue()).getField();
                        if(field1.equals(field)){
                            // System.out.println("okFound");
                            //System.out.println(stmt.getClass());
                            if(stmt instanceof JAssignStmt){
                                JAssignStmt jStmt = (JAssignStmt) stmt;
                                Value value = jStmt.getRightOp();
                                backwardFlowTrack(stmt,value,method);
                            }
                            else {
                                //   System.out.println("Something is not ok!");
                            }
                        }
                    }
                }
            }
        }
    }

    private  String isAnnotationBindView(SootField field) {
        for (Tag tag : field.getTags()) {
            // Check if the field has a @BindView annotation
            if (tag instanceof VisibilityAnnotationTag) {
                VisibilityAnnotationTag annotationTag = (VisibilityAnnotationTag) tag;
                //System.out.println("Sss=> " +tag.toString());
                for (AnnotationTag annotation : annotationTag.getAnnotations()) {
                    if (annotation.getType().contains("BindView")) {
                        int index =tag.toString().indexOf("value: ");
                        if(index != -1){
                            //System.out.println("www=> "+ index);
                            //   System.out.println("Alireza: BindView" );
                            return tag.toString().substring(index+7).trim();
                        }
                    }
                }
            }
        }
        return "-1";
    }

    public Stmt getStmt(){
        return this.stmt;
    }
}
