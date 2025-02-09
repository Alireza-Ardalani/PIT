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

        Value defValue = null;
        SootMethod method = infoflowCFG.getMethodOf(sourceStmt);
        if(sourceStmt instanceof JAssignStmt){
            JAssignStmt jAssignStmt = (JAssignStmt)sourceStmt;
            InvokeExpr invokeExpr = jAssignStmt.getInvokeExpr();
            if(invokeExpr instanceof JVirtualInvokeExpr){
                JVirtualInvokeExpr jVirtualInvokeExpr = (JVirtualInvokeExpr)invokeExpr;
                defValue = jVirtualInvokeExpr.getBase();
            }
        }
        else if(sourceStmt instanceof JInvokeStmt){
            JInvokeStmt jInvokeStmt = (JInvokeStmt) sourceStmt;
            InvokeExpr invokeExpr = jInvokeStmt.getInvokeExpr();
            if(invokeExpr instanceof JVirtualInvokeExpr){
                JVirtualInvokeExpr jVirtualInvokeExpr = (JVirtualInvokeExpr)invokeExpr;
                defValue = jVirtualInvokeExpr.getBase();
            }
        }
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
        Body body = method.retrieveActiveBody();
        PatchingChain<Unit> units = body.getUnits();
        List<Unit> reversedUnits = new ArrayList<>(units);
        Collections.reverse(reversedUnits);
        boolean startReached = false;
        boolean originFound = false;

        for (Unit unit : reversedUnits) {
            Stmt stmt = (Stmt) unit;

            if (stmt == startStmt) {
                startReached = true;
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
                        sootFields.add(field);
                        originFound = true;
                        trackFieldDefinition_(field);
                        break;
                    }

                    if (rightOp instanceof InvokeExpr) {
                        SootMethod invokedMethod = ((InvokeExpr) rightOp).getMethod();

                        if(invokedMethod.getSignature().contains("findViewById")){
                            findViewByIdStmts.add(stmt);
                            originFound = true;
                            break;
                        }

                        if (invokedMethod.isConcrete() && invokedMethod.hasActiveBody()) {
                            trackReturnValue(invokedMethod);
                        } else if (invokedMethod.getSignature().contains("findViewById")) {
                            findViewByIdStmts.add(stmt);
                        } else {
                        }
                        originFound = true;
                        break;
                    }

                    if (rightOp instanceof IdentityRef && rightOp instanceof ParameterRef) {
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
                        resolveParameter(method, (ParameterRef) rightOp);
                        originFound = true;
                        break;
                    }

                    if (rightOp instanceof ThisRef) {
                        SootClass enclosingClass = method.getDeclaringClass();

                        for (SootField field : enclosingClass.getFields()) {
                            if (field.getType().equals(targetObject.getType())) {
                                originFound = true;
                                break;
                            }
                        }

                        if (!originFound) {
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
                        String classObject = "empty";
                        if(infoflowCFG.getMethodOf(stmt).getDeclaringClass().toString().equals("dummyMainClass")){
                            String[] split = infoflowCFG.getMethodOf(stmt).getSignature().toString().split(" ");
                            classObject = split[1];
                        }
                        else {
                            classObject = infoflowCFG.getMethodOf(stmt).getDeclaringClass().toString();
                        }
                        //TODO
                        break;
                    }
                }
            }
        }

    }


    private  void resolveParameter(SootMethod method, ParameterRef paramRef) {
        int paramIndex = paramRef.getIndex();
        Collection<Unit> callSites = infoflowCFG.getCallersOf(method);
        if (callSites.isEmpty()) {
            extraCallFinder(method,paramIndex);
            return;
        }
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
                continue;
            }
            Value argument = invokeExpr.getArg(paramIndex);
            SootMethod callerMethod = infoflowCFG.getMethodOf(stmt);
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
                                continue;
                            }
                            Value argument = invokeExpr.getArg(paramIndex);
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
            viewIDNumbers.add(annotationID);
            return ;
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
                            }
                        }
                    }
                }
            }
        }
    }

    private  String isAnnotationBindView(SootField field) {
        for (Tag tag : field.getTags()) {
            if (tag instanceof VisibilityAnnotationTag) {
                VisibilityAnnotationTag annotationTag = (VisibilityAnnotationTag) tag;
                for (AnnotationTag annotation : annotationTag.getAnnotations()) {
                    if (annotation.getType().contains("BindView")) {
                        int index =tag.toString().indexOf("value: ");
                        if(index != -1){
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
