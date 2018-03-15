package com.hiveworkshop.blizzard.blp;

import com.hiveworkshop.lang.LocalizedText;

/**
 * Interface for accepting all warnings.
 * 
 * @author Imperial Good
 */
interface WarningListener {
	/**
	 * Send a warning message.
	 * 
	 * @param text
	 *            Warning message.
	 */
	public void sendWarning(LocalizedText<BLPText> text);
}
