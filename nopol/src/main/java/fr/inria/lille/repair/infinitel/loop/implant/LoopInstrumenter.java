package fr.inria.lille.repair.infinitel.loop.implant;


import static fr.inria.lille.commons.spoon.util.SpoonModelLibrary.newBreak;
import static fr.inria.lille.commons.spoon.util.SpoonModelLibrary.newExpressionFromSnippet;
import static fr.inria.lille.commons.spoon.util.SpoonModelLibrary.newIf;
import static fr.inria.lille.commons.spoon.util.SpoonModelLibrary.newLiteral;
import static fr.inria.lille.commons.spoon.util.SpoonModelLibrary.newLocalVariableDeclaration;
import static fr.inria.lille.commons.spoon.util.SpoonModelLibrary.newStatementFromSnippet;
import static fr.inria.lille.commons.spoon.util.SpoonModelLibrary.setLoopBody;
import static fr.inria.lille.commons.spoon.util.SpoonModelLibrary.setLoopingCondition;
import static fr.inria.lille.commons.spoon.util.SpoonStatementLibrary.insertAfterUnderSameParent;
import static fr.inria.lille.commons.spoon.util.SpoonStatementLibrary.insertBefore;
import static fr.inria.lille.commons.spoon.util.SpoonStatementLibrary.insertBeforeUnderSameParent;
import static java.lang.String.format;

import java.util.Collection;

import spoon.reflect.code.CtBreak;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtWhile;
import spoon.reflect.factory.Factory;
import xxl.java.support.Singleton;
import fr.inria.lille.commons.spoon.collectable.CollectableValueFinder;
import fr.inria.lille.commons.trace.RuntimeValues;
import fr.inria.lille.commons.trace.RuntimeValuesInstrumenter;

public class LoopInstrumenter {
	
	public static void instrument(LoopMonitor loopMonitor, RuntimeValues<?> runtimeValues) {
		int monitorID = loopMonitor.instanceID();
		String counterName = "loopEntrancesCounter_" + monitorID;
		String conditionName = "loopConditionEvaluation_" + monitorID;
		CtWhile astLoop = loopMonitor.loop().astLoop();
		Factory factory = astLoop.getFactory();
		boolean unbreakable = loopMonitor.loop().isUnbreakable();
		Collection<String> collectables = collectableFinder().findFromWhile(astLoop);
		CtIf newIf = loopBodyWrapper(factory, astLoop, loopMonitor, counterName, conditionName, unbreakable);
		appendMonitoredExit(factory, loopMonitor, counterName, unbreakable);
		declareEntrancesCounter(factory, astLoop, loopMonitor, counterName);
		appendCounterIncrementation(newIf, loopMonitor, counterName);
		traceReachableValues(collectables, conditionName, runtimeValues, newIf);
	}
	
	private static CtIf loopBodyWrapper(Factory factory, CtWhile loop, LoopMonitor loopMonitor, String counterName, String conditionName, boolean unbreakable) {
		CtExpression<Boolean> monitoredCondition = newExpressionFromSnippet(factory, conditionName, Boolean.class);
		CtIf newIf = newIf(factory, monitoredCondition, loop.getBody(), newBreak(factory));
		if (unbreakable) {
			newIf.setElseStatement(null);
		}
		setLoopBody(loop, newIf);
		declareConditionEvaluation(factory, loop, loopMonitor, counterName, conditionName, newIf);
		setLoopingCondition(loop, newLiteral(factory, true));
		return newIf;
	}
	
	private static void declareConditionEvaluation(Factory factory, CtWhile loop, LoopMonitor loopMonitor, String counterName, String conditionName, CtIf newIf) {
		String conditionInvocation = loopMonitor.invocationOnLoopConditionEvaluation(loop.getLoopingExpression().toString(), counterName);
		String declaration = format("boolean %s = %s", conditionName, conditionInvocation);
		CtStatement counterCreation = newStatementFromSnippet(factory, declaration);
		insertBeforeUnderSameParent(counterCreation, newIf);
	}
	
	private static void appendMonitoredExit(Factory factory, LoopMonitor loopMonitor, String counterName, boolean unbreakable) {
		CtWhile astLoop = loopMonitor.loop().astLoop();
		if (! unbreakable) {
			CtStatement monitoredExit = newStatementFromSnippet(astLoop.getFactory(), loopMonitor.invocationOnFirstStatementAfterLoop(counterName));
			insertAfterUnderSameParent(monitoredExit, astLoop);
		}
		appendMonitoredReturnExit(factory, loopMonitor, counterName);
		appendMonitoredBreakExit(factory, loopMonitor, counterName);
	}
	
	private static void appendMonitoredReturnExit(Factory factory, LoopMonitor loopMonitor, String counterName) {
		for (CtReturn<?> returnStatement : loopMonitor.loop().returnStatements()) {
			CtStatement invocationOnLoopReturn = newStatementFromSnippet(factory, loopMonitor.invocationOnLoopReturn(counterName));
			insertBeforeUnderSameParent(invocationOnLoopReturn, returnStatement);
		}
	}

	private static void appendMonitoredBreakExit(Factory factory, LoopMonitor loopMonitor, String counterName) {
		for (CtBreak breakStatement : loopMonitor.loop().breakStatements()) {
			CtStatement invocationOnLoopBreak = newStatementFromSnippet(factory, loopMonitor.invocationOnLoopBreak(counterName));
			insertBeforeUnderSameParent(invocationOnLoopBreak, breakStatement);
		}
	}

	private static void declareEntrancesCounter(Factory factory, CtWhile loop, LoopMonitor loopMonitor, String counterName) {
		CtLocalVariable<Integer> counterCreation = newLocalVariableDeclaration(factory, int.class, counterName, 0, loop.getParent());
		insertBeforeUnderSameParent(counterCreation, loop);
	}

	private static void appendCounterIncrementation(CtIf newIf, LoopMonitor loopMonitor, String counterName) {
		CtStatement increment = newStatementFromSnippet(newIf.getFactory(), format("%s += 1", counterName));
		CtStatement then = newIf.getThenStatement();
		insertBefore(increment, then, then);
	}
	
	private static void traceReachableValues(Collection<String> collectables, String conditionName, RuntimeValues<?> runtimeValues, CtIf newIf) {
		RuntimeValuesInstrumenter.runtimeCollectionBefore(newIf, collectables, conditionName, runtimeValues);
	}
	
	private static CollectableValueFinder collectableFinder() {
		return Singleton.of(CollectableValueFinder.class);
	}
}