package ru.rexchange.tools;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

public class StringUtils {
	public static String toUpperCamelCase(String s) {
		String[] parts = s.split("_");
		StringBuilder camelCaseString = new StringBuilder();
		for (String part : parts) {
			camelCaseString.append(toProperCase(part));
		}
		return camelCaseString.toString();
	}

	public static String toLowerCamelCase(String s) {
		String[] parts = s.split("_");
		StringBuilder camelCaseString = new StringBuilder(parts[0].toLowerCase());
		for (int i = 1; i < parts.length; i++) {
			camelCaseString.append(toProperCase(parts[i]));
		}
		return camelCaseString.toString();
	}

	static String toProperCase(String s) {
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
	}

	public static String toDelimitedString(String[] strings, String delimiter) {
		if (strings == null || strings.length == 0)
			return "";
		StringBuilder sb = new StringBuilder(strings[0]);
		for (int i = 1; i < strings.length; i++)
			sb.append(delimiter).append(strings[i]);
		return sb.toString();
	}

	public static String toStringCommon(Collection collection) {
		if (collection == null)
			return "null";
		return Arrays.toString(collection.toArray(new Object[0]));
	}

	public static String toString(Collection<Float> collection) {
		if (collection == null)
			return "null";
		return toString(collection.toArray(new Float[0]));
	}

	public static String toString(float[] array) {
		return toString(array, 5);
	}

	public static String toString(Float[] array) {
		return toString(array, 5);
	}

	public static String toString(float[] array, int precision) {
		if (array == null)
			return "null";

		int iMax = array.length - 1;
		if (iMax == -1)
			return "[]";

		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0; ; i++) {
			b.append(String.format(Locale.ROOT, "%." + precision + "f", array[i]));
			if (i == iMax)
				return b.append(']').toString();
			b.append(", ");
		}
	}

	public static String toString(Float[] array, int precision) {
		if (array == null)
			return "null";

		int iMax = array.length - 1;
		if (iMax == -1)
			return "[]";

		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0; ; i++) {
			b.append(String.format(Locale.ROOT, "%." + precision + "f", array[i]));
			if (i == iMax)
				return b.append(']').toString();
			b.append(", ");
		}
	}

	public static String printAsPercent(float factor) {
		return String.format("%.2f%%", factor * 100f);
	}

	@NotNull
  public static String getShortUUID() {
    return UUID.randomUUID().toString().replaceAll("-", "");
  }
}
