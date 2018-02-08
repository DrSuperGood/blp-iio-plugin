package com.hiveworkshop.blizzard.blp;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.Arrays;

/**
 * BLP compliant index (palette) color model. Functions similar to the standard
 * IndexColorModel except permits the use of an optional separate alpha channel
 * of various bit depths.
 * <p>
 * BLP files with indexed color content always use a 256 index color palette of
 * 8 bit per channel RGB values and an optional separate 1, 4 or 8 bit alpha
 * channel to determine pixel color. The underlying Raster must use 8 bit
 * samples for index with appropriately sized samples for alpha.
 * 
 * @author Imperial Good
 */
public final class BLPIndexColorModel extends ColorModel {
	/**
	 * Number of bits used for palette entries.
	 */
	public static final int PALETTE_INDEX_BITS = 8;

	/**
	 * Maximum palette length for this ColorModel.
	 */
	public static final int MAX_PALETTE_LENGTH = 1 << PALETTE_INDEX_BITS;

	/**
	 * Internal ColorModel used to decode palette and alpha values. Handles much
	 * of the required form conversions.
	 */
	private final ColorModel internalColorModel;

	/**
	 * Palette used to lookup color. In form compatible with internalColorModel.
	 */
	private final int[] palette;

	/**
	 * Normalized component cache for the palette colors converted into sRGB
	 * ColorSpace. This is used to speed up conversion from component values to
	 * color index. Linearly perceived color components are preferred when
	 * choosing a color index which is why sRGB is used.
	 */
	private float[] normalizedComponentCache = null;

	/**
	 * Produces a ColorModel suitable for producing palette entries in the
	 * specified ColorSpace. The ColorModel can be used to decode or encode
	 * colors in the palette.
	 * <p>
	 * The ColorSpace must be an RGB type.
	 * 
	 * @param colorSpace
	 *            RGB color space of the palette.
	 * @return ColorModel suitable for interacting with the palette.
	 */
	public static ColorModel createPaletteColorModel(
			final ColorSpace colorSpace) {
		if (colorSpace.getType() != ColorSpace.TYPE_RGB) {
			throw new IllegalArgumentException("Unsupported color space type.");
		}
		return new DirectColorModel(colorSpace, 24, 0x00FF0000, 0x0000FF00,
				0x000000FF, 0, false, DataBuffer.TYPE_INT);
	}

	/**
	 * Construct a universal palette with the specified number of color levels.
	 * <p>
	 * A color must have at least 2 levels, allowing for 0.0f and 1.0f
	 * intensities. The total product of all color levels must be less than
	 * <code>MAX_PALETTE_LENGTH</code>.
	 * <p>
	 * Color levels are distributed evenly within the sRGB ColorSpace. These are
	 * then converted for use with colorSpace.
	 * 
	 * @param redLevels
	 *            Number of red levels.
	 * @param greenLevels
	 *            Number of green levels.
	 * @param blueLevels
	 *            Number of blue levels.
	 * @param colorSpace
	 *            Intended color space of the palette.
	 * @return Populated palette.
	 */
	public static int[] createUniversalPaletteColorMap(final int redLevels,
			final int greenLevels, final int blueLevels,
			final ColorSpace colorSpace) {
		if (colorSpace.getType() != ColorSpace.TYPE_RGB) {
			throw new IllegalArgumentException("Unsupported color space type.");
		} else if (redLevels < 2) {
			throw new IllegalArgumentException("Invalid redLevels value.");
		} else if (greenLevels < 2) {
			throw new IllegalArgumentException("Invalid greenLevels value.");
		} else if (blueLevels < 2) {
			throw new IllegalArgumentException("Invalid blueLevels value.");
		} else if (redLevels * greenLevels * blueLevels > MAX_PALETTE_LENGTH) {
			throw new IllegalArgumentException("Requires too many colors.");
		}

		final int[] palette = new int[MAX_PALETTE_LENGTH];
		final float[] sRGBComponents = new float[3];
		final ColorModel colorMapColorModel = createPaletteColorModel(
				colorSpace);
		int i = 0;
		for (int r = 0; r < redLevels; r += 1) {
			sRGBComponents[0] = (float) r / (float) (redLevels - 1);
			for (int g = 0; g < greenLevels; g += 1) {
				sRGBComponents[1] = (float) g / (float) (greenLevels - 1);
				for (int b = 0; b < blueLevels; b += 1) {
					sRGBComponents[2] = (float) b / (float) (blueLevels - 1);
					palette[i++] = colorMapColorModel.getDataElement(
							colorSpace.fromRGB(sRGBComponents), 0);
				}
			}
		}

		return palette;
	}

	/**
	 * Utility method to convert an alpha bits amount into a transparency mode
	 * as defined by Transparency interface.
	 * 
	 * @param alphaBits
	 *            Number of bits in alpha channel.
	 * @return Transparency mode.
	 */
	private static int resolveTransparency(int alphaBits) {
		if (alphaBits == 0) {
			return Transparency.OPAQUE;
		} else if (alphaBits == 1) {
			return Transparency.BITMASK;
		}
		return Transparency.TRANSLUCENT;
	}

	/**
	 * Constructs a linear RGB BLP indexed ColorModel from a palette. Up to the
	 * <code>MAX_PALETTE_LENGTH</code> colors may be used. Each index of the
	 * palette is in the form of 0xBBGGRR.
	 * <p>
	 * A palette of null will allocate a universal 8-8-4 palette. This will be
	 * sufficient to hold any image with vague color accuracy. For best results
	 * it is recommended to use an adaptive palette for the target image.
	 * <p>
	 * Alpha bits is the precision of the alpha channel. Valid values are 0, 1,
	 * 4 and 8.
	 * 
	 * @param palette
	 *            Palette this ColorModel will use.
	 * @param alphaBits
	 *            Precision of alpha component in bits.
	 */
	public BLPIndexColorModel(final int[] palette, final int alphaBits) {
		this(palette, alphaBits,
				ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB));
	}

	/**
	 * Constructs a BLP indexed ColorModel from a palette. Up to the
	 * <code>MAX_PALETTE_LENGTH</code> colors may be used. Each index of the
	 * palette is in the form of 0xBBGGRR.
	 * <p>
	 * A palette of null will allocate a universal 8-8-4 palette. This will be
	 * sufficient to hold any image with vague color accuracy. For best results
	 * it is recommended to use an adaptive palette for the target image.
	 * <p>
	 * Alpha bits is the precision of the alpha channel. Valid values are 0, 1,
	 * 4 and 8.
	 * 
	 * @param palette
	 *            Palette this ColorModel will use.
	 * @param alphaBits
	 *            Precision of alpha component in bits.
	 * @param colorSpace
	 *            RGB color space used by this ColorModel.
	 */
	public BLPIndexColorModel(final int[] palette, final int alphaBits,
			final ColorSpace colorSpace) {
		super(8 + alphaBits,
				alphaBits == 0 ? new int[] { 8, 8, 8 }
						: new int[] { 8, 8, 8, alphaBits },
				colorSpace, alphaBits != 0, false,
				resolveTransparency(alphaBits), DataBuffer.TYPE_BYTE);

		if (!BLPEncodingType.INDEXED.isAlphaBitsValid(alphaBits)) {
			throw new IllegalArgumentException("Unsupported alphaBits value.");
		} else if (colorSpace.getType() != ColorSpace.TYPE_RGB) {
			throw new IllegalArgumentException("Unsupported color space type.");
		}

		if (palette != null) {
			if (palette.length > MAX_PALETTE_LENGTH) {
				throw new IllegalArgumentException(
						"Unsupported palette length.");
			}
			this.palette = Arrays.copyOf(palette, MAX_PALETTE_LENGTH);
		} else {
			this.palette = createUniversalPaletteColorMap(8, 8, 4, colorSpace);
		}

		internalColorModel = new DirectColorModel(colorSpace, 24 + alphaBits,
				0x00FF0000, 0x0000FF00, 0x000000FF, (1 << alphaBits) - 1 << 24,
				false, DataBuffer.TYPE_INT);
	}

	/**
	 * Utility method to construct an internal pixel.
	 * 
	 * @param index
	 *            Palette index element.
	 * @param alpha
	 *            Alpha element.
	 * @return Internal pixel.
	 */
	private int constructInternalPixel(final int index, int alpha) {
		return getPaletteColor(index) | alpha << 24;
	}

	@Override
	public SampleModel createCompatibleSampleModel(int w, int h) {
		return new BLPPackedSampleModel(w, h,
				hasAlpha()
						? new int[] { PALETTE_INDEX_BITS,
								getComponentSize(getNumColorComponents()) }
						: new int[] { PALETTE_INDEX_BITS },
				null);
	}

	@Override
	public WritableRaster createCompatibleWritableRaster(int w, int h) {
		SampleModel sm = createCompatibleSampleModel(w, h);
		return Raster.createWritableRaster(sm, sm.createDataBuffer(), null);
	}

	/**
	 * Produces a ColorModel suitable for processing palette entries in the
	 * specified ColorSpace. The ColorModel can be used to decode or encode
	 * colors in the palette.
	 * 
	 * @return ColorModel suitable for interacting with the palette.
	 */
	public ColorModel createPaletteColorModel() {
		return createPaletteColorModel(getColorSpace());
	}

	@Override
	public int getAlpha(int pixel) {
		return internalColorModel.getAlpha(pixelToInternalPixel(pixel));
	}

	@Override
	public int getAlpha(Object inData) {
		return internalColorModel.getAlpha(pixelToInternalPixel(inData));
	}

	@Override
	public WritableRaster getAlphaRaster(WritableRaster raster) {
		if (hasAlpha())
			return raster.createWritableChild(raster.getMinX(),
					raster.getMinY(), raster.getWidth(), raster.getHeight(),
					raster.getMinX(), raster.getMinY(), new int[] { 1 });
		else
			return null;
	}

	/**
	 * Get number of bands in a pixel of this ColorModel.
	 * 
	 * @return Band count.
	 */
	public int getBandNumber() {
		return hasAlpha() ? 2 : 1;
	}

	/**
	 * Finds the index of the best matching color to what was requested. This
	 * may be very slow but allows for maximum compatibility.
	 * <p>
	 * Comparison is done in a visually linear ColorSpace sRGB.
	 * <p>
	 * The algorithms used are for basic color quantization support. Efficiency
	 * is only a minor consideration and the accuracy of the results is not
	 * measured. The results should be vaguely what one can expect for indexed
	 * ColorModels. For best indexed color quantization a separate algorithm
	 * should be used with the results fed to this color model.
	 * 
	 * @param normComponents
	 *            normalized components
	 * @param normOffset
	 *            offset in normalized components array
	 * @return index of closest matching color
	 */
	private int getBestPaletteIndex(float[] normComponents, int normOffset) {
		// need color cache
		populateComponentCache();

		// Prepare desired sRGB components.
		float[] desiredComponents;
		desiredComponents = Arrays.copyOfRange(normComponents, normOffset,
				normOffset + 3);
		if (!getColorSpace().isCS_sRGB()) {
			desiredComponents = getColorSpace().toRGB(desiredComponents);
		}

		// Search for closest match.
		int best = -1;
		int nComponents = getColorSpace().getNumComponents();
		float bestDiff = Float.MAX_VALUE;
		for (int i = 0; i < palette.length; i += 1) {
			int cacheOffset = i * nComponents;

			// compare color channels using euclidian distance
			float diff = 0f;
			for (int component = 0; component < nComponents; component += 1) {
				float delta = normalizedComponentCache[cacheOffset + component]
						- desiredComponents[component];
				diff += delta * delta;
			}
			diff = (float) Math.sqrt(diff);

			// find best result
			if (diff < bestDiff) {
				best = i;
				bestDiff = diff;
			}
		}

		return best;
	}

	@Override
	public int getBlue(int pixel) {
		return internalColorModel.getBlue(pixelToInternalPixel(pixel));
	}

	@Override
	public int getBlue(Object inData) {
		return internalColorModel.getBlue(pixelToInternalPixel(inData));
	}

	@Override
	public int[] getComponents(int pixel, int[] components, int offset) {
		return internalColorModel.getComponents(pixelToInternalPixel(pixel),
				components, offset);
	}

	@Override
	public int[] getComponents(Object pixel, int[] components, int offset) {
		return internalColorModel.getComponents(pixelToInternalPixel(pixel),
				components, offset);
	}

	@Override
	public int getDataElement(float[] normComponents, int normOffset) {
		int pixel = getBestPaletteIndex(normComponents, normOffset);
		if (hasAlpha()) {
			pixel |= ((internalColorModel.getDataElement(normComponents,
					normOffset) >> 24) & 0xFF) << 8;
		}

		return pixel;
	}

	@Override
	public int getDataElement(int[] components, int offset) {
		int pixel = getBestPaletteIndex(internalColorModel
				.getNormalizedComponents(components, offset, null, 0), 0)
				& 0xFF;

		if (hasAlpha())
			pixel |= components[offset + 3] << 8;

		return pixel;
	}

	@Override
	public Object getDataElements(float[] normComponents, int normOffset,
			Object obj) {
		if (obj == null) {
			obj = new byte[getBandNumber()];
		}
		byte[] bytepixel = (byte[]) obj;

		bytepixel[0] = (byte) getBestPaletteIndex(normComponents, normOffset);

		if (hasAlpha()) {
			bytepixel[1] = (byte) (internalColorModel.getUnnormalizedComponents(
					normComponents, normOffset, null,
					0)[getNumColorComponents()]);
		}

		return obj;
	}

	@Override
	public Object getDataElements(final int rgb, Object pixel) {
		if (pixel == null) {
			pixel = new byte[getBandNumber()];
		}
		byte[] bytepixel = (byte[]) pixel;

		Object rgbpixel = internalColorModel.getDataElements(rgb, null);

		bytepixel[0] = (byte) getBestPaletteIndex(
				internalColorModel.getNormalizedComponents(rgbpixel, null, 0),
				0);

		if (hasAlpha()) {
			bytepixel[1] = (byte) internalColorModel.getComponents(rgbpixel,
					null, 0)[getNumColorComponents()];
		}

		return pixel;
	}

	@Override
	public Object getDataElements(int[] components, int offset, Object obj) {
		if (obj == null) {
			obj = new byte[getBandNumber()];
		}
		byte[] bytepixel = (byte[]) obj;

		bytepixel[0] = (byte) getBestPaletteIndex(internalColorModel
				.getNormalizedComponents(components, offset, null, 0), 0);

		if (hasAlpha())
			bytepixel[1] = (byte) components[offset + getNumColorComponents()];

		return obj;
	}

	@Override
	public int getGreen(int pixel) {
		return internalColorModel.getGreen(pixelToInternalPixel(pixel));
	}

	@Override
	public int getGreen(Object inData) {
		return internalColorModel.getGreen(pixelToInternalPixel(inData));
	}

	/**
	 * Return a copy of the palette used. Color indices correspond with pixel
	 * values and the returned array will always have a length of
	 * <code>MAX_PALETTE_LENGTH</code>. Indices can be processed using a color
	 * model returned from <b>createPaletteColorModel</b>.
	 * 
	 * @return Array of palette colors.
	 */
	public int[] getPalette() {
		return palette.clone();
	}

	/**
	 * Lookup a color in the palette.
	 * 
	 * @param index
	 *            Index of requested color.
	 * @return Color compatible with <code>paletteColorModel</code>.
	 */
	private int getPaletteColor(int index) {
		return palette[index] & 0xFFFFFF;
	}

	@Override
	public int getRed(int pixel) {
		return internalColorModel.getRed(pixelToInternalPixel(pixel));
	}

	@Override
	public int getRed(Object inData) {
		return internalColorModel.getRed(pixelToInternalPixel(inData));
	}

	@Override
	public int getRGB(int pixel) {
		return internalColorModel.getRGB(pixelToInternalPixel(pixel));
	}

	@Override
	public int getRGB(Object inData) {
		return internalColorModel.getRGB(pixelToInternalPixel(inData));
	}

	@Override
	public boolean isCompatibleRaster(Raster raster) {
		return isCompatibleSampleModel(raster.getSampleModel())
				&& raster.getNumBands() == getBandNumber();
	}

	@Override
	public boolean isCompatibleSampleModel(SampleModel sm) {
		// validate number of bands
		final int bands = getBandNumber();
		if (sm.getNumBands() != bands) {
			return false;
		}

		// transfer type must always be TYPE_BYTE
		if (sm.getTransferType() != DataBuffer.TYPE_BYTE) {
			return false;
		}

		// check index band size
		if (sm.getSampleSize(0) != PALETTE_INDEX_BITS)
			return false;
		// check alpha band size
		if (hasAlpha() && sm
				.getSampleSize(1) != getComponentSize(getNumColorComponents()))
			return false;

		return true;
	}

	/**
	 * Utility method to convert an input pixel into an internal pixel for
	 * processing.
	 * 
	 * @param pixel
	 *            Input pixel.
	 * @return Internal pixel.
	 */
	private int pixelToInternalPixel(final int pixel) {
		final int index = pixel & 0xFF;
		final int alpha = hasAlpha() ? pixel >>> 8 & 0xFF : 0;
		return constructInternalPixel(index, alpha);
	}

	/**
	 * Utility method to convert an input pixel into an internal pixel for
	 * processing.
	 * 
	 * @param inData
	 *            Array of pixel values.
	 * @return Internal pixel.
	 */
	private int pixelToInternalPixel(final Object inData) {
		final byte[] pixel = (byte[]) inData;
		final int index = Byte.toUnsignedInt(pixel[0]);
		final int alpha = hasAlpha() ? Byte.toUnsignedInt(pixel[1]) : 0;
		return constructInternalPixel(index, alpha);
	}

	/**
	 * Populates the color component cache used to help select which color index
	 * to use to represent a color. This cache is populated only when converting
	 * from components to a pixel.
	 */
	private void populateComponentCache() {
		// only initialize once, this is expensive
		if (normalizedComponentCache != null)
			return;

		final int nColorComponents = internalColorModel.getNumColorComponents();
		final int nComponents = internalColorModel.getNumComponents();
		normalizedComponentCache = new float[palette.length * nColorComponents];

		int[] componentCacheArray = new int[nComponents];
		float[] normCacheArray = new float[nComponents];
		for (int i = 0; i < palette.length; i += 1) {
			// normalize pixel
			int pixel = getPaletteColor(i);
			internalColorModel
					.getNormalizedComponents(
							internalColorModel.getComponents(pixel,
									componentCacheArray, 0),
							0, normCacheArray, 0);

			// translate color components to sRGB
			final float[] srgbComponents = internalColorModel.getColorSpace()
					.toRGB(normCacheArray);

			// cache
			final int offset = i * nColorComponents;
			System.arraycopy(srgbComponents, 0, normalizedComponentCache,
					offset, nColorComponents);
		}
	}

}
