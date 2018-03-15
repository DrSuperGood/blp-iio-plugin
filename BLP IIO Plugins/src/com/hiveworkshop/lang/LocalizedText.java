package com.hiveworkshop.lang;

/**
 * Class representing some localized text to resolve into a string at a later
 * time.
 * <p>
 * The text is in the form of an Enum. The enum's name represents a localized
 * mapping. A number of arguments can be provided to format the text with.
 * 
 * @author Imperial Good
 */
public class LocalizedText<E extends Enum<?> & TextEnum> {
	/**
	 * The text enum to lookup.
	 */
	private final E textEnum;

	/**
	 * Format arguments to resolve against the text enum.
	 */
	private final Object[] args;

	/**
	 * Constructs a new localized text object using the specified text enum and
	 * formating arguments.
	 * 
	 * @param textEnum
	 *            Text enum.
	 * @param args
	 *            Formating arguments.
	 */
	public LocalizedText(final E textEnum, final Object... args) {
		this.textEnum = textEnum;
		this.args = args;
	}

	/**
	 * Uses the specified localizer to produce a localized string.
	 * 
	 * @param localizer
	 *            Localizer.
	 * @return Localized string.
	 */
	public String localize(final LocalizedTextLocalizer<E> localizer) {
		return localizer.localize(textEnum, args);
	}
}
