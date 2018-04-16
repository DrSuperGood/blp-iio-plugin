package com.hiveworkshop.blizzard.blp;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * A class that is responsible for processing between mipmap data and mipmap
 * rasters or images.
 * <p>
 * Implementations of this class are responsible for the types of images that
 * can be processed. A single instance is responsible for processing all mipmaps
 * of the same BLP file.
 * <p>
 * Mipmap rasters represent the most native form of the mipmap data. For
 * malformed files this might not always make sense. Instead it is recommended
 * that the mipmap raster is converted into a mipmap image for easier use.
 * 
 * @author Imperial Good
 */
abstract class MipmapProcessor {
	/**
	 * The default color space used to generate mipmap images
	 * <p>
	 * Most BLP files are created using sRGB content, even if they are then
	 * incorrectly processed as linear RGB by graphics APIs.
	 */
	private static final ColorSpace DEFAULT_COLOR_SPACE = ColorSpace.getInstance(ColorSpace.CS_sRGB);

	/**
	 * Scales an image dimension to be for a given mipmap level.
	 * 
	 * @param dimension
	 *            the dimension to scale in pixels.
	 * @param level
	 *            the mipmap level.
	 * @return the mipmap dimension in pixels.
	 */
	private static int scaleImageDimension(final int dimension, final int mipmapIndex) {
		return Math.max(dimension >>> mipmapIndex, 1);
	}

	/**
	 * Color space used to generate mipmap images.
	 */
	protected ColorSpace decodeColorSpace = DEFAULT_COLOR_SPACE;

	/**
	 * If true then generated mipmap images might be subjected to per pixel content
	 * checks.
	 */
	protected boolean deepCheck = true;

	/**
	 * Width of the full scale image.
	 */
	private int width = 1;

	/**
	 * Height of the full scale image.
	 */
	private int height = 1;

	/**
	 * Number of mipmap images to represent the full scale image.
	 */
	private int mipmapCount = 1;

	/**
	 * Warning listener used to accept warning messages.
	 */
	protected final WarningListener listener;

	/**
	 * Construct a mipmap processor with the given warning listener.
	 * 
	 * @param listener
	 *            Warning listener.
	 */
	protected MipmapProcessor(final WarningListener listener) {
		this.listener = listener;
	}

	/**
	 * Check if the provided raster can be used to generate mipmap data.
	 * 
	 * @param rast
	 *            Raster to check.
	 * @return True if the raster can be used.
	 */
	public abstract boolean canUseRaster(Raster rast);

	/**
	 * Decodes the given mipmap data into a writable raster.
	 * <p>
	 * The returned writable raster is produced with the minimal processing
	 * possible. It might be backed by the provided mipmap data array. Not all
	 * pixels or data elements may be valid. An unused alpha band might be present.
	 * Blue and red bands likely are swapped. It might have the wrong dimensions for
	 * the mipmap level.
	 * 
	 * @param mipmapData
	 *            Mipmap data array obtained from a mipmap manager.
	 * @param mipmapIndex
	 *            Mipmap index number.
	 * @return A writable raster with mipmap pixels.
	 * @throws IOException
	 *             If an exception occurred during decoding.
	 */
	public abstract Raster decodeMipmapToRaster(byte[] mipmapData, int mipmapIndex) throws IOException;

	/**
	 * Converts a list of mipmap rasters into a list mipmap data arrays.
	 * <p>
	 * The number of rasters provided must be either 1 or the mipmap count of this
	 * processor. A value of 1 can be used to process images without mipmaps. The
	 * order of the raster list must be the same as the order of the mipmaps. All
	 * mipmap image rasters must be compatible with this processor and must have
	 * correct mipmap image dimensions.
	 * <p>
	 * The image write param is used to control values such as compression quality
	 * and type used by this processor. All other parts of it will be ignored.
	 * 
	 * @param mipmapRasters
	 *            List of mipmap rasters to encode.
	 * @param param
	 *            Parameter used to control encoding quality.
	 * @return List of mipmap data arrays.
	 * @throws IOException
	 *             If an exception occurred during encoding.
	 */
	public abstract List<byte[]> encodeRastersToMipmaps(List<Raster> mipmapRasters, ImageWriteParam param)
			throws IOException;

	/**
	 * Converts a mipmap raster into a usable mipmap image.
	 * <p>
	 * The returned buffered image may be backed by the mipmap raster. The image
	 * will always be the correct dimensions with all pixels valid. The image will
	 * draw visually correct. The backing data buffer may only be correct if deep
	 * check is enabled.
	 * 
	 * @param mipmapRaster
	 *            Mipmap raster which pixels are sourced from.
	 * @param mipmapIndex
	 *            Mipmap index number.
	 * @return Prepared mipmap image.
	 * @throws IOException
	 *             If an exception occurred while generating the image.
	 */
	public abstract BufferedImage generateMipmapImage(Raster mipmapRaster, int mipmapIndex) throws IOException;

	/**
	 * Get the height of the specified mipmap image.
	 * 
	 * @param mipmapIndex
	 *            Mipmap index number.
	 * @return The height of the mipmap image.
	 */
	public int getHeight(final int mipmapIndex) {
		return scaleImageDimension(height, mipmapIndex);
	}

	/**
	 * Get the number of mipmap levels supported by this processor.
	 * <p>
	 * This is the number of mipmap images one can use this processor for and not
	 * the number available. If mipmaps are to be used at all is controlled by logic
	 * higher up.
	 * 
	 * @return Number of mipmap levels this processor supports.
	 */
	public int getMipmapCount() {
		return mipmapCount;
	}

	/**
	 * An iterator of the image types supported by this processor.
	 * <p>
	 * The types in the iterator can be used to both encode and decode mipmaps.
	 * 
	 * @return iterator of supported image types.
	 */
	public abstract Iterator<ImageTypeSpecifier> getSupportedImageTypes();

	/**
	 * Get the width of the specified mipmap image.
	 * 
	 * @param mipmapIndex
	 *            Mipmap index number.
	 * @return The width of the mipmap image.
	 */
	public int getWidth(final int mipmapIndex) {
		return scaleImageDimension(width, mipmapIndex);
	}

	/**
	 * Utility method to check a list of mipmap rasters.
	 * <p>
	 * This makes sure that the list has the correct number of rasters and that
	 * those rasters are the correct sizes.
	 * 
	 * @param mipmapRasters
	 *            List of mipmap rasters.
	 * @return True if the list is valid, otherwise false.
	 */
	protected boolean isMipmapRasterListValid(final List<Raster> mipmapRasters) {
		// Check count.
		final var rasterCount = mipmapRasters.size();
		if (rasterCount != 1 && rasterCount != getMipmapCount()) {
			return false;
		}

		// Check individual rasters.
		for (var i = 0; i < rasterCount; i += 1) {
			final Raster raster = mipmapRasters.get(i);
			if (raster.getWidth() != getWidth(i) || raster.getHeight() != getHeight(i) || !canUseRaster(raster)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Prepares a raster to be encoded into mipmap data. This means enforcing the
	 * use of an appropriately formated data buffer and sample model with the the
	 * required band count, band sizes and band order.
	 * <p>
	 * The returned raster might be a child of the provided raster. No size checks
	 * are performed, it is assumed that the image raster is already the required
	 * size.
	 * 
	 * @param imageRaster
	 *            Image raster.
	 * @return Mipmap raster.
	 */
	public abstract Raster prepareRasterToEncode(Raster imageRaster);

	public abstract void readObject(ImageInputStream src) throws IOException;

	/**
	 * Set the color space used to generate mipmap images.
	 * <p>
	 * The color space must have 3 components.
	 * 
	 * @param colorspace
	 *            Color space of generated mipmap images, or <code>null</code> to
	 *            return to default.
	 */
	public void setColorSpace(final ColorSpace colorspace) {
		if (colorspace == null) {
			decodeColorSpace = DEFAULT_COLOR_SPACE;
		} else if (colorspace.getNumComponents() != DEFAULT_COLOR_SPACE.getNumComponents()) {
			throw new IllegalArgumentException("Parameter colorspace has wrong number of components.");
		} else {
			decodeColorSpace = colorspace;
		}
	}

	/**
	 * Enable the processor to perform per pixel content checking when generating
	 * mipmap images.
	 * <p>
	 * Performing such checks adds significant overhead but may be required to warn
	 * of and prevent some malformed samples in underlying data buffers. Even with
	 * deep checking turned off, returned images will appear correct within the
	 * standard Java API.
	 * 
	 * @param enable
	 *            True if deep checking should be performed.
	 */
	public void setDeepChecking(final boolean enable) {
		deepCheck = enable;
	}

	/**
	 * Set the dimensions of the full scale image of this processor.
	 * 
	 * @param width
	 *            Width of the full scale image in pixels.
	 * @param height
	 *            Height of the full scale image in pixels.
	 */
	public void setDimensions(final int width, final int height) {
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("Invalid image dimensions.");
		}

		this.width = width;
		this.height = height;

		mipmapCount = Integer.SIZE - Integer.numberOfLeadingZeros(Math.max(width, height));
	}

	public abstract void writeObject(ImageOutputStream dst) throws IOException;
}
