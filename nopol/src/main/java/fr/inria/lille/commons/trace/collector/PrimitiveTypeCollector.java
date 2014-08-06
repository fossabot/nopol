package fr.inria.lille.commons.trace.collector;

import java.util.Map;

public abstract class PrimitiveTypeCollector extends ValueCollector {

	public void addValue(String name, Object value, Map<String, Object> storage) {
		storage.put(name, value);
		storage.put("true", true);
		/* Workaround for primitive types, because otherwise there are missing indices in RuntimeValues.
		 * We add a fake value so as to have the same number of collected values at runtime.
		 * If SMT uses this value, the resulting synthesized expression would still compile
		 * XXX can be safely removed when NOPOL is integrated with the new model
		 */
	}
}