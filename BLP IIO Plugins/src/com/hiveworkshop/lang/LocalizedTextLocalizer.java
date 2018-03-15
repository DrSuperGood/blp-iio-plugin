package com.hiveworkshop.lang;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Class to resolve some localized text against a resource bundle.
 * 
 * @author Imperial Good
 */
public class LocalizedTextLocalizer<E extends Enum<?> & TextEnum> {
	/**
	 * Localized resource bundle.
	 */
	private final ResourceBundle bundle;

	/**
	 * Construct a localizer from the given resource bundle base name and locale.
	 * 
	 * @param baseName
	 *            Resource bundle base name.
	 * @param locale
	 *            Desired locale.
	 */
	public LocalizedTextLocalizer(final String baseName, final Locale locale) {
		ResourceBundle bundle = null;
		try {
			bundle = ResourceBundle.getBundle(baseName, locale);
		} catch (MissingResourceException e) {

		}
		this.bundle = bundle;
	}

	/**
	 * Construct a localized string from a text enum and formating arguments.
	 * 
	 * @param textEnum
	 *            Text enum.
	 * @param args
	 *            Formating arguments.
	 * @return Localized string.
	 */
	public String localize(final E textEnum, final Object... args) {
		final String key = textEnum.name();
		String text = null;
		if (bundle != null) {
			try {
				text = bundle.getString(key);
				if (args.length > 0) {
					text = String.format(text, args);
				}
			} catch (MissingResourceException e) {

			}
		}

		if (text == null) {
			text = String.format("missing resource {%S, %S}", key, args);
		}

		return text;
	}

	/**
	 * Localize the given localized text.
	 * 
	 * @param text
	 *            Localized text.
	 * @return Localized string.
	 */
	public String localize(final LocalizedText<E> text) {
		return text.localize(this);
	}
}
