package fr.inria.lille.repair.infinitel;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;

import spoon.reflect.code.CtWhile;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.factory.Factory;
import xxl.java.container.classic.MetaCollection;
import xxl.java.container.classic.MetaMap;
import xxl.java.container.various.Bag;
import xxl.java.junit.TestCase;
import fr.inria.lille.commons.spoon.util.SpoonElementLibrary;
import fr.inria.lille.commons.spoon.util.SpoonModelLibrary;
import fr.inria.lille.commons.synthesis.CodeGenesis;
import fr.inria.lille.repair.Main;
import fr.inria.lille.repair.ProjectReference;
import fr.inria.lille.repair.infinitel.loop.While;
import fr.inria.lille.repair.infinitel.loop.examination.LoopTestResult;
import fr.inria.lille.repair.infinitel.loop.implant.MonitoringTestExecutor;
import fr.inria.lille.repair.infinitel.loop.implant.ProjectMonitorImplanter;

public class InfinitelTest {
	
	@Ignore
	@Test
	public void realProject() {
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
		String pwd = "/Users/virtual/Desktop/piggybank/";
		String src = pwd + "src/main/java/";
		String ab = pwd + "target/classes/";
		String ac = pwd + "target/test-classes/";
		String[] deps = new String[] {"pig-0.12.0-SNAPSHOT-withdependencies.jar", "pig-0.12.0-SNAPSHOT.jar",
									  "hive-exec-0.8.0.jar", "hsqldb-1.8.0.10.jar", "jetty-util-6.1.26.jar", "json-simple-1.1.jar"};
		String dependency = "";
		for (String dep : deps) {
			dependency += ":" + pwd + "lib/" + dep;
		}
		dependency = dependency.substring(1);
		Main.main(new String[] {"infinitel", src, dependency + ":" + ac + ":" + ab, "z3", pwd + "lib/z3_for_mac"});
	}
	
	@Test
	public void unbreakableLoops() {
		Infinitel infinitel = infinitel(1);
		ProjectMonitorImplanter implanter = new ProjectMonitorImplanter(0);
		Map<String, CtWhile> loops = loopsByMethodIn(infinitel.project().sourceFile(), 4);
		assertFalse(implanter.isUnbreakable(loops.get("loopResult")));
		assertFalse(implanter.isUnbreakable(loops.get("fixableInfiniteLoop")));
		assertTrue(implanter.isUnbreakable(loops.get("unfixableInfiniteLoop")));
		assertTrue(implanter.isUnbreakable(loops.get("otherUnfixableInfiniteLoop")));
	}
	
	@Test
	public void nestedLoopIsNotInfiniteInExample3() {
		Infinitel infinitel = infinitel(3);
		MonitoringTestExecutor testExecutor = infinitel.newTestExecutor();
		LoopTestResult testResult = infinitel.newTestResult(testExecutor);
		assertEquals(2, testResult.numberOfLoops());
		Collection<While> loops = testResult.infiniteLoops();
		assertEquals(1, loops.size());
		While loop = MetaCollection.any(loops);
		assertEquals(7, loop.position().getLine());
	}
	
	@Test
	public void numberOfReturnsInExample1() {
		Infinitel infinitel = infinitel(1);
		MonitoringTestExecutor executor = infinitel.newTestExecutor();
		Map<Integer, Integer> numberOfReturns = MetaMap.newHashMap(asList(8, 16, 26, 37), asList(0, 2, 2, 2));
		Collection<While> allLoops = executor.monitor().allLoops();
		assertEquals(numberOfReturns.size(), allLoops.size());
		assertEquals(3, While.loopsWithReturn(allLoops).size());
		for (While loop : allLoops) {
			assertEquals(numberOfReturns.get(loop.position().getLine()).intValue(), loop.returnStatements().size());
		}
	}
	
	@Test
	public void theBreakMustBeForTheWhile() {
		Infinitel infinitel = infinitel(2);
		MonitoringTestExecutor executor = infinitel.newTestExecutor();
		Map<Integer, Integer> numberOfBreaks = MetaMap.newHashMap(asList(7, 14, 38, 50), asList(0, 1, 0, 0));
		Collection<While> allLoops = executor.monitor().allLoops();
		assertEquals(numberOfBreaks.size(), allLoops.size());
		assertEquals(1, While.loopsWithBreak(allLoops).size());
		for (While loop : allLoops) {
			assertEquals(numberOfBreaks.get(loop.position().getLine()).intValue(), loop.breakStatements().size());
		}
	}
	
	@Test
	public void theReturnMustBeForTheWhile() {
		Infinitel infinitel = infinitel(2);
		MonitoringTestExecutor executor = infinitel.newTestExecutor();
		Map<Integer, Integer> numberOfReturns = MetaMap.newHashMap(asList(7, 14, 38, 50), asList(0, 2, 0, 0));
		Collection<While> allLoops = executor.monitor().allLoops();
		assertEquals(numberOfReturns.size(), allLoops.size());
		assertEquals(1, While.loopsWithReturn(allLoops).size());
		for (While loop : allLoops) {
			assertEquals(numberOfReturns.get(loop.position().getLine()).intValue(), loop.returnStatements().size());
		}
	}
	
	@Test
	public void numberOfBreaksInExample3() {
		Infinitel infinitel = infinitel(3);
		MonitoringTestExecutor executor = infinitel.newTestExecutor();
		Map<Integer, Integer> numberOfBreaks = MetaMap.newHashMap(asList(7, 8), asList(1, 0));
		Collection<While> allLoops = executor.monitor().allLoops();
		assertEquals(numberOfBreaks.size(), allLoops.size());
		assertEquals(1, While.loopsWithBreak(allLoops).size());
		for (While loop : allLoops) {
			assertEquals(numberOfBreaks.get(loop.position().getLine()).intValue(), loop.breakStatements().size());
		}
	}
	
	@Test
	public void bookkeepingInLoopsOfExample3() {
		Infinitel infinitel = infinitel(3);
		MonitoringTestExecutor testExecutor = infinitel.newTestExecutor();
		LoopTestResult testResult = infinitel.newTestResult(testExecutor);
		int threshold = infinitel.configuration().iterationsThreshold();
		Map<Integer, Bag<Integer>> records = MetaMap.newHashMap();
		Bag<Integer> topLoopRecords = Bag.newHashBag(asList(1,threshold), asList(2, 1));
		Bag<Integer> nestedLoopRecords = Bag.newHashBag(asList(0, 1, 10), asList(threshold, 1, 1));
		records.put(7, topLoopRecords);
		records.put(8, nestedLoopRecords);
		assertEquals(2, testExecutor.monitor().allLoops().size());
		for (While loop : testExecutor.monitor().allLoops()) {
			assertEquals(records.get(loop.position().getLine()), testResult.aggregatedExitRecordsOf(loop));
		}
	}
	
	private Map<String, CtWhile> loopsByMethodIn(File sourceFile, int numberOfLoops) {
		Factory model = SpoonModelLibrary.modelFor(sourceFile);
		Collection<CtPackage> allRoots = model.Package().getAllRoots();
		assertEquals(1, allRoots.size());
		Collection<CtWhile> elements = SpoonElementLibrary.allChildrenOf(MetaCollection.any(allRoots), CtWhile.class);
		assertEquals(numberOfLoops, elements.size());
		Map<String, CtWhile> byMethod = MetaMap.newHashMap();
		for (CtWhile loop : elements) {
			String methodName = loop.getParent(CtMethod.class).getSimpleName();
			byMethod.put(methodName, loop);
		}
		return byMethod;
	}
	
	@Test
	public void infinitelExample4() {
		InfiniteLoopFixer fixer = infiniteLoopFixerForExample(4);
		LoopTestResult testResult = fixer.testResult();
		Collection<While> allLoops = fixer.executor().monitor().allLoops();
		assertEquals(1, allLoops.size());
		While loop = MetaCollection.any(allLoops);
		assertEquals(1, While.loopsWithBreak(allLoops).size());
		assertEquals(1, While.loopsWithReturn(allLoops).size());
		assertEquals(1, While.loopsWithBreakAndReturn(allLoops).size());
		assertEquals(0, While.loopsWithoutBodyExit(allLoops).size());
		assertEquals(2, testResult.aggregatedNumberOfReturnExits(loop));
		assertEquals(Bag.newHashBag(1, 4), testResult.aggregatedReturnRecordsOf(loop));
		assertEquals(2,  testResult.aggregatedNumberOfBreakExits(loop));
		assertEquals(Bag.newHashBag(1, 3), testResult.aggregatedBreakRecordsOf(loop));
		assertEquals(6, testResult.aggregatedNumberOfRecords(loop));
		assertEquals(Bag.newHashBag(0,1,1,3,4,6), testResult.aggregatedExitRecordsOf(loop));
		List<String> testNames = asList("returnExitIn1", "returnExitIn4", "breakExitIn1", "breakExitIn3", "normalExitIn0", "normalExitIn6");
		Map<String, Integer> expected = expectedIterationsMap(4, testNames, asList(1, 4, 1, 3, 0, 6));
		for (String testName : expected.keySet()) {
			TestCase testCase = testWithName(testName, testResult);
			assertEquals((long) expected.get(testName), testResult.numberOfIterationsIn(loop, testCase));
		}
	}

	@Test
	public void infinitelExample1() {
		/** This test is very slow with some versions of CVC4 */
		Map<String, Integer> expected = expectedIterationsMap(1, asList("testNegative"), asList(4));
		CodeGenesis fix = checkInfinitel(1, 8, 4, 1, expected);
		checkFix(fix, asList("(b != a)&&(((a)<=((1)+(1)))||((b)<(a)))",
							 "((!(((a)-(1))<(b)))||(((a)-(1))<=(1)))&&(b != a)",
							 "((b != a)&&(((1)+(1))!=((a)-(1))))||((b)<=((a)-(1)))",
							 "!((((a)+(-1))<(b))&&((((1)-((a)+(-1)))<=(-1))||((a)==(b))))"));
	}
	
	@Test
	public void infinitelExample2() {
		Map<String, Integer> expected = expectedIterationsMap(2, asList("infiniteLoop"), asList(1));
		CodeGenesis fix = checkInfinitel(2, 7, 1, 1, expected);
		checkFix(fix, asList("(a == 0)"));
	}
	
	@Test
	public void infinitelExample3() {
		Map<String, Integer> expected = expectedIterationsMap(3, asList("doesNotReachZeroReturnCopy"), asList(0));
		CodeGenesis fix = checkInfinitel(3, 7, 3, 0, expected);
		checkFix(fix, asList("(-1)<(aCopy)", "(1)<=(a)"));
	}
	
	private CodeGenesis checkInfinitel(int infinitelExample, int infiniteLoopLine, int passingTests, int failingTests, Map<String, Integer> testThresholds) {
		InfiniteLoopFixer fixer = infiniteLoopFixerForExample(infinitelExample);
		LoopTestResult testResult = fixer.testResult();
		assertEquals(failingTests, testResult.numberOfFailedTests());
		assertEquals(passingTests, testResult.numberOfSuccessfulTests());
		Collection<While> infiniteLoops = testResult.infiniteLoops();
		assertEquals(1, infiniteLoops.size());
		While infiniteLoop = MetaCollection.any(infiniteLoops);
		assertEquals(infiniteLoopLine, infiniteLoop.position().getLine());
		checkSuccessfulIterations(testThresholds, infiniteLoop, fixer, testResult);
		return fixer.fixInfiniteLoop(infiniteLoop);
	}
	
	private void checkFix(CodeGenesis fix, Collection<String> fixes) {
		assertTrue(fix.isSuccessful());
		System.out.println(fix.returnStatement());
		assertTrue(fixes.contains(fix.returnStatement()));
	}
	
	private void checkSuccessfulIterations(Map<String, Integer> expected, While loop, InfiniteLoopFixer fixer, LoopTestResult testResult) {
		Map<TestCase, Integer> nonHaltingTests = testResult.nonHaltingTestsOf(loop);
		for (String testName : expected.keySet()) {
			TestCase testCase = testWithName(testName, testResult);
			int actual = fixer.firstSuccessfulIteration(loop, testCase, nonHaltingTests.get(testCase));
			assertEquals((long) expected.get(testName), actual);
		}
	}
	
	private Infinitel infinitel(int exampleNumber) {
		String sourcePath = format("../test-projects/src/main/java/infinitel_examples/infinitel_example_%d/InfinitelExample.java", exampleNumber);
		String classPath = "../test-projects/target/classes/:../test-projects/target/test-classes/";
		String testClass = format("infinitel_examples.infinitel_example_%d.InfinitelExampleTest", exampleNumber);
		ProjectReference project = new ProjectReference(sourcePath, classPath, new String[] { testClass });
		return new Infinitel(project);
	}
	
	private InfiniteLoopFixer infiniteLoopFixerForExample(int exampleNumber) {
		Infinitel infinitel = infinitel(exampleNumber);
		MonitoringTestExecutor testExecutor = infinitel.newTestExecutor();
		LoopTestResult testResult = infinitel.newTestResult(testExecutor);
		return new InfiniteLoopFixer(testResult, testExecutor);
	}

	private Map<String, Integer> expectedIterationsMap(int exampleNumber, List<String> testNames, List<Integer> iterations) {
		Map<String, Integer> expectedMap = MetaMap.newHashMap();
		assertEquals(testNames.size(), iterations.size());
		for (int i = 0; i < testNames.size(); i += 1) {
			expectedMap.put(testNames.get(i), iterations.get(i));
		}
		return expectedMap;
	}
	
	private TestCase testWithName(String testName, LoopTestResult testResult) {
		for (TestCase testCase : testResult.testCases()) {
			if (testCase.testName().equals(testName)) {
				return testCase;
			}
		}
		throw new RuntimeException("Test case not found: " + testName);
	}
}