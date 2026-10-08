package dev.matanyah.eef;

import java.util.Collection;
import java.util.Objects;
import java.util.regex.Pattern;

/** One typed predicate over a dotted event-context path. */
public record EefCondition(String path, Operator operator, Object value) {
	public enum Operator {
		EXISTS, NOT_EXISTS, EQ, NE, GT, GTE, LT, LTE, IN, NOT_IN, CONTAINS, NOT_CONTAINS, INTERSECTS, MATCHES
	}

	public EefCondition {
		if (path == null || path.isBlank()) throw new IllegalArgumentException("Condition path is blank");
		Objects.requireNonNull(operator, "Condition operator is missing");
	}

	public boolean test(Object actual) {
		return switch (operator) {
			case EXISTS -> actual != null;
			case NOT_EXISTS -> actual == null;
			case EQ -> equalValues(actual, value);
			case NE -> !equalValues(actual, value);
			case GT -> actual != null && value != null && compare(actual, value) > 0;
			case GTE -> actual != null && value != null && compare(actual, value) >= 0;
			case LT -> actual != null && value != null && compare(actual, value) < 0;
			case LTE -> actual != null && value != null && compare(actual, value) <= 0;
			case IN -> value instanceof Collection<?> collection && collection.stream().anyMatch(item -> equalValues(actual, item));
			case NOT_IN -> !(value instanceof Collection<?> collection) || collection.stream().noneMatch(item -> equalValues(actual, item));
			case CONTAINS -> actual instanceof Collection<?> collection
				? collection.stream().anyMatch(item -> equalValues(item, value))
				: actual != null && String.valueOf(actual).contains(String.valueOf(value));
			case NOT_CONTAINS -> actual instanceof Collection<?> collection
				? collection.stream().noneMatch(item -> equalValues(item, value))
				: actual == null || !String.valueOf(actual).contains(String.valueOf(value));
			case INTERSECTS -> actual instanceof Collection<?> actualValues && value instanceof Collection<?> expectedValues
				&& actualValues.stream().anyMatch(actualValue -> expectedValues.stream().anyMatch(expected -> equalValues(actualValue, expected)));
			case MATCHES -> actual != null && value != null && Pattern.compile(String.valueOf(value)).matcher(String.valueOf(actual)).matches();
		};
	}

	private static boolean equalValues(Object left, Object right) {
		if (left instanceof Number a && right instanceof Number b) return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
		return Objects.equals(left, right) || left != null && right != null && String.valueOf(left).equals(String.valueOf(right));
	}

	private static int compare(Object left, Object right) {
		if (left instanceof Number a && right instanceof Number b) return Double.compare(a.doubleValue(), b.doubleValue());
		if (left == null || right == null) return Integer.MIN_VALUE;
		return String.valueOf(left).compareTo(String.valueOf(right));
	}
}
