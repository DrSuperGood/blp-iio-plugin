package com.hiveworkshop.blizzard.blp;

import javax.imageio.ImageReadParam;

/**
 * ImageReadParam for BLP images. Adds functionality to customize decode
 * behavior and to optimize throughput.
 * <p>
 * Read operations can also be instructed to be direct read. In this mode
 * standard image read parameter behavior and expensive tests are ignored.
 * 
 * @author Imperial Good
 */
public class BLPReadParam extends ImageReadParam {
	/**
	 * Controls whether ImageReadParam mechanics can be ignored for improved
	 * performance.
	 */
	protected boolean directRead = false;

	/**
	 * Constructs a default BLPReadParam.
	 * <p>
	 * The ImageReadParam state is the same as its default constructor. No JPEG
	 * ImageReaderSpi overwrite is set. Direct read mode is disabled.
	 */
	public BLPReadParam() {
	}

	/**
	 * Return if direct read mechanics apply.
	 * 
	 * @return If direct read mode is active.
	 */
	public boolean isDirectRead() {
		return directRead;
	}

	/**
	 * Allows the enabling of direct read mode.
	 * <p>
	 * When direct read mode is enabled, standard image read param mechanics are
	 * ignored and some warning checks are disabled. This potentially allows image
	 * read operations to be performed more efficiently.
	 * <p>
	 * By default direct read is disabled. Using a ImageReadParam that is not of
	 * type BLPReadParam implies that direct read is disabled. If performance is
	 * required then direct read mode should be explicitly enabled using this
	 * method.
	 * <p>
	 * An example use case would be loading blp images for use as GPU textures. One
	 * always wants to load the full texture into memory. By using direct read some
	 * image copies and tests can be skipped saving potentially non trivial CPU
	 * time.
	 * 
	 * @param directRead
	 *            If direct reading should occur.
	 */
	public void setDirectRead(final boolean directRead) {
		this.directRead = directRead;
	}

}
