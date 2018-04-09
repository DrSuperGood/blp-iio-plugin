package com.hiveworkshop.blizzard.blp;

import com.hiveworkshop.lang.TextEnum;

/**
 * Localized text enums used by BLP IIO plugin.
 * 
 * @author Imperial Good
 */
public enum BLPText implements TextEnum {
	/**
	 * JPEG content BLP declared as opaque but has 1 or more non-opaque pixels.
	 */
	BAD_PIXEL_ALPHA,
	/**
	 * Internally used JPEG reader/writer warning.
	 */
	JPEG_WARNING,
	/**
	 * Mipmap image has wrong sample dimensions.
	 */
	BAD_MIPMAP_DIMENSION,
	/**
	 * JPEG content shared header might be too big to be loaded by some readers.
	 */
	BAD_JPEG_HEADER,
	/**
	 * Image data buffer for a mipmap image is not the expected size.
	 */
	BAD_DATA_BUFFER;
}
