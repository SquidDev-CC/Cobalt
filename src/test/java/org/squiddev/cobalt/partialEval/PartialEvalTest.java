package org.squiddev.cobalt.partialEval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.squiddev.cobalt.ScriptHelper;

public class PartialEvalTest {
	private ScriptHelper helpers;

	@BeforeEach
	public void setup() {
		helpers = new ScriptHelper("/partial-eval/");
		helpers.setup();
	}

	@ParameterizedTest(name = ParameterizedTest.ARGUMENTS_WITH_NAMES_PLACEHOLDER)
	@ValueSource(strings = {"hello-world", "fibonacci"})
	public void helloWorld(String name) throws Exception {
		helpers.runComparisonTest(name);
	}
}
