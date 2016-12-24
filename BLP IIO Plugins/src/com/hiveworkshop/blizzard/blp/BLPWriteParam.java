package com.hiveworkshop.blizzard.blp;

import javax.imageio.ImageWriteParam;
import javax.imageio.spi.ImageWriterSpi;

/**
 * ImageWriteParam for BLP images. Adds functionality to customize encode
 * behavior and encode quality.
 * <p>
 * A JPEG ImageWriterSpi can be specified to generate ImageWriters to encode
 * JPEG content BLP files with. By default a generically obtained JPEG
 * ImageWriterSpi will be used. Useful if multiple JPEG writers are installed
 * and using a specific one is desired or necessary. The JPEG ImageWriter
 * returned from the ImageWriterSpi must support writing Rasters.
 * <p>
 * Write operations can be specified to automatically generate mipmaps. When
 * specified it is only valid to write a single image to the file at index 0.
 * All required mipmaps, if any, will be automatically generated using an area
 * averaging algorithm. Better mipmap results might be obtain from other
 * algorithms and explicitly specifying the mipmap images. Automatic mipmap
 * generation is specified on by default for ease of use.
 * <p>
 * Write operations can be specified to automatically optimize the full scale
 * image dimensions to the maximum usable dimensions when no StreamMetadata is
 * provided. Supported resizing modes include NONE, RATIO and CLAMP. NONE will
 * use the image provided unmodified. RATIO will resize down an image to the
 * maximum useful dimensions while keeping aspect ratio. CLAMP will resize down
 * an image to the maximum useful dimensions, treating each dimension
 * separately. All resizing is done using an area averaging algorithm. CLAMP is
 * specified by default for ease of use and maximum quality as BLP file aspect
 * ratio does not usually matter.
 * 
 * @author Imperial Good
 */
public class BLPWriteParam extends ImageWriteParam {
	/**
	 * The JPEG ImageWriterSpi to use to encode JPEG content.
	 */
	private ImageWriterSpi jpegSpi = null;

	/**
	 * The automatic scale optimization settings for image 0 if no
	 * StreamMetadata is specified.
	 */
	public static enum ScaleOptimization {
		/**
		 * No scale optimization is performed.
		 */
		NONE,
		/**
		 * Down-sample to largest useful size, maintaining aspect ratio.
		 */
		RATIO,
		/**
		 * Down-sample to largest useful size.
		 */
		CLAMP;
	}

	/**
	 * The scale optimization setting to use on image 0 when no StreamMetadata
	 * is present.
	 */
	private ScaleOptimization scaleOpt = ScaleOptimization.CLAMP;

	/**
	 * The auto mipmap setting to use on image 0.
	 */
	private boolean autoMipmap = true;

	public BLPWriteParam() {
		canWriteCompressed = true;
	}

	/**
	 * Get the ImageWriterSpi used to encode JPEG content BLPs.
	 * 
	 * @return the JPEG ImageWriterSpi.
	 */
	public ImageWriterSpi getJPEGSpi() {
		return jpegSpi;
	}

	/**
	 * Set the ImageWriterSpi used to encode JPEG content. This can allow the
	 * ImageWriter used to encode JPEG content to be customized for reliability
	 * or performance reasons.
	 * <p>
	 * The ImageWriterSpi must be able to encode JPEG image files. Setting to
	 * null will cause a JPEG ImageWriter to be obtained automatically if
	 * installed.
	 * 
	 * @param jpegSpi
	 *            the ImageWriterSpi to use for JPEG content.
	 */
	public void setJPEGSpi(ImageWriterSpi jpegSpi) {
		this.jpegSpi = jpegSpi;
	}

	/**
	 * Get the current scale optimization being used.
	 * 
	 * @return scale optimization to be used.
	 */
	public ScaleOptimization getScaleOptimization() {
		return scaleOpt;
	}

	/**
	 * Set the scale optimization setting to use.
	 * <p>
	 * See ScaleOptimization enums for their mechanical details.
	 * 
	 * @param scaleOpt
	 *            the scale optimization setting to use.
	 */
	public void setScaleOptimization(ScaleOptimization scaleOpt) {
		this.scaleOpt = scaleOpt;
	}

	/**
	 * Returns if auto mipmap generation is being used.
	 * 
	 * @return true if mipmaps will be automatically generated from image 0.
	 */
	public boolean isAutoMipmap() {
		return autoMipmap;
	}

	/**
	 * Set if mipmaps should be automatically generated from a full scale image.
	 * <p>
	 * When true mipmaps will be automatically generate from image 0 using an
	 * area averaging algorithm. Trying to set any other image than 0 is not
	 * allowed.
	 * 
	 * @param autoMipmap
	 *            the automatic mipmap generation setting to use.
	 */
	public void setAutoMipmap(boolean autoMipmap) {
		this.autoMipmap = autoMipmap;
	}
}
