package com.alirezaard.pit;

import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.solver.cfg.InfoflowCFG;
import soot.jimple.internal.*;

import java.util.*;

public class ViewID {
    InfoflowCFG infoflowCFG;
    public Set<SootField> sootFields = new HashSet<>() ;
    public Set<String> viewIDNumbers = new HashSet<>() ;
    public Set<String> viewIDs = new HashSet<>() ;

    public ViewID(InfoflowCFG inputInfoflowCFG) {

        this.infoflowCFG = inputInfoflowCFG;
    }
    public void run(Stmt sourceStmt, JADX jadx){
        innerRun(sourceStmt);
        for (String viewIDNumber : viewIDNumbers){
             viewIDs.add(jadx.findElementID(viewIDNumber));
        }
    }

    private  void innerRun(Stmt sourceStmt) {
        Value defValue = null;
        SootMethod method = infoflowCFG.getMethodOf(sourceStmt);
        if(sourceStmt instanceof JAssignStmt){
            JAssignStmt jAssignStmt = (JAssignStmt)sourceStmt;
            InvokeExpr invokeExpr = jAssignStmt.getInvokeExpr();
            if(invokeExpr instanceof JVirtualInvokeExpr){
                JVirtualInvokeExpr jVirtualInvokeExpr = (JVirtualInvokeExpr)invokeExpr;
                // defValue = jVirtualInvokeExpr.getBase();
                int argCount = jVirtualInvokeExpr.getArgCount();
                if(argCount==1){
                    defValue =  jVirtualInvokeExpr.getArg(0);
                }
                else {
                    System.out.println("Something is not ok!!!");
                }
            }
            else {System.err.println("Error, Bad Cast Handling: It is not JVirtualInvokeExpr");}
        }
        else if(sourceStmt instanceof JInvokeStmt){
            JInvokeStmt jInvokeStmt = (JInvokeStmt) sourceStmt;
            InvokeExpr invokeExpr = jInvokeStmt.getInvokeExpr();
            if(invokeExpr instanceof JVirtualInvokeExpr){
                JVirtualInvokeExpr jVirtualInvokeExpr = (JVirtualInvokeExpr)invokeExpr;
                defValue = jVirtualInvokeExpr.getBase();
                int argCount = jVirtualInvokeExpr.getArgCount();
                if(argCount==1){
                    defValue =  jVirtualInvokeExpr.getArg(0);
                }
                else {
                    System.out.println("Something is not ok!!!");
                }
            }
            else {System.err.println("LOG: Error, Bad Cast Handling: It is not JVirtualInvokeExpr");}
        }
        else{System.err.println("LOG: Error, Bad Cast Handling: It is not JAssignStmt");}

        if(defValue instanceof IntConstant){
            //System.out.println("ID--> " + defValue);
            viewIDNumbers.add(defValue.toString());
        }
        else {
            backwardFlowTrack(sourceStmt,defValue,method);
        }
    }

    private  void backwardFlowTrack(Stmt startStmt, Value targetObject, SootMethod method) {
        if (startStmt == null || targetObject == null || method == null) {
            System.out.println("LOG: Error: startStmt, targetObject, or method is null.");
            return;
        }
        Body body = method.retrieveActiveBody();
        PatchingChain<Unit> units = body.getUnits();

        List<Unit> reversedUnits = new ArrayList<>(units);
        Collections.reverse(reversedUnits);

        boolean startReached = false;
        boolean originFound = false;

        for (Unit unit : reversedUnits) {
            if(targetObject instanceof IntConstant){
                viewIDNumbers.add(targetObject.toString());
                break;
            }
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
                        if(field.toString().contains("R$id") || field.getType() instanceof IntType){
                            viewIDs.add(field.getName());
                        }
                        else {
                            sootFields.add(field);
                        }
                        // sootFields.add(field);
                        originFound = true;
                        trackFieldDefinition_(field);
                        break;
                    }

                    if (rightOp instanceof InvokeExpr) {
                        SootMethod invokedMethod = ((InvokeExpr) rightOp).getMethod();
                        if (invokedMethod.isConcrete() && invokedMethod.hasActiveBody()) {
                            trackReturnValue(invokedMethod);
                        }  else {
                            System.out.println("LOG: Cannot track further; method is not concrete: " + invokedMethod.getSignature());
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
                        //System.out.println("LOG: Origin found: method parameter (param" + paramIndex + ")");
                        resolveParameter(method, (ParameterRef) rightOp);
                        originFound = true;
                        break;
                    }

                    if (rightOp instanceof ThisRef) {
                        SootClass enclosingClass = method.getDeclaringClass();
                        for (SootField field : enclosingClass.getFields()) {
                            if (field.getType().equals(targetObject.getType())) {
                                originFound = true;
                                if(field.toString().contains("R$id") || field.getType() instanceof IntType ){
                                    viewIDs.add(field.getName());
                                }
                                else {
                                    sootFields.add(field);
                                }
                                break;
                            }
                        }

                        if (!originFound) {
                            System.out.println("LOG: No matching field found in class: " + enclosingClass);
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
                        // TODO
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
                continue;
            }

            Value argument = invokeExpr.getArg(paramIndex);
            SootMethod callerMethod = infoflowCFG.getMethodOf(stmt);
            i++;
            backwardFlowTrack(stmt, argument, callerMethod);
        }
    }

    private  void trackReturnValue(SootMethod method) {
        Body body = method.retrieveActiveBody();
        PatchingChain<Unit> units = body.getUnits();
        for (Unit unit : units) {
            if (unit instanceof JReturnStmt) {
                JReturnStmt returnStmt = (JReturnStmt) unit;
                Value returnValue = returnStmt.getOp();
                backwardFlowTrack(returnStmt, returnValue, method);
            }
        }
    }

    private  void trackFieldDefinition_(SootField field) {


        SootClass clazz = field.getDeclaringClass();
        for (SootMethod method : clazz.getMethods()) {
            try {
                method.retrieveActiveBody();
            } catch (Exception exception) {
                continue;
            }

            if (!method.hasActiveBody()) {
                continue;
            }
            Body body = method.getActiveBody();
            PatchingChain<Unit> units = body.getUnits();
            for (Unit unit : units) {
                Stmt stmt = (Stmt) unit;
                List<ValueBox> defs = stmt.getDefBoxes();
                for (ValueBox def : defs) {
                    if (def.getValue() instanceof FieldRef) {
                        SootField field1 = ((FieldRef) def.getValue()).getField();
                        if (field1.equals(field)) {
                            if (stmt instanceof JAssignStmt) {
                                JAssignStmt jStmt = (JAssignStmt) stmt;
                                Value value = jStmt.getRightOp();
                                backwardFlowTrack(stmt, value, method);
                            } else {
                                System.out.println("Something is wrong!");
                            }
                        }
                    }
                }
            }
        }
    }
    public Collection<? extends SootField> getSootFields() {
        return sootFields;
    }
    public Collection<String> getViewIDNumbers() {
        return viewIDNumbers;
    }
    public Collection<String> getViewIDs() {
        return viewIDs;
    }
}
