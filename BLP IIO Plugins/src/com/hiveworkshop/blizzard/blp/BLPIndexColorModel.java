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

import static com.hiveworkshop.blizzard.blp.BLPCommon.INDEXED_PALETTE_SIZE;

/**
 * BLP compliant index (palette) color model. Functions similar to the standard
 * IndexColorModel. Permits the use of an optional separate alpha channel.
 * Forces BLP indexed color restrictions.
 * <p>
 * BLP files with indexed color content use a 256 index color palette of 8 bit
 * per channel RGB values and an optional separate 1, 4 or 8 bit alpha channel
 * to determine pixel color. The underlying Raster uses 8 bit samples for index
 * with appropriately sized samples for alpha.
 * 
 * @author ImperialGood
 */
public final class BLPIndexColorModel extends ColorModel {
	/**
	 * Internal DirectColorModel to decode BLP pixels into components.
	 */
	protected final DirectColorModel indexedColorModel;
	
	/**
	 * Color map for indexed color. In form of BLP_DIRECT_CM.
	 */
	private final int[] colorMap;

	/**
	 * Normalized component cache for the colorMap colors converted into sRGB
	 * ColorSpace. This is used to speed up conversion from component values to
	 * color index. Linearly perceived color components are preferred when
	 * choosing a color index which is why sRGB is used.
	 */
	private float[] normalizedComponentCache = null;
	
	private static int resolveTransparency(int alphaBits) {
		if (alphaBits == 0) {
			return Transparency.OPAQUE;
		} else if(alphaBits == 1) {
			return Transparency.BITMASK;
		}
		return Transparency.TRANSLUCENT;
	}

	/**
	 * Constructs a BLP indexed ColorModel from a BLP color map array. Up to the
	 * first 256 indices will be used. Each index is in the form of 0xRRGGBB.
	 * <p>
	 * Alpha bits means alpha channel precision. Valid values are 0, 1, 4 and 8.
	 * 
	 * @param cmap
	 *            color map array.
	 * @param alphaBits
	 *            alpha component precision in bits.
	 */
	public BLPIndexColorModel(int[] cmap, int alphaBits) {
		//super(8 + alphaBits, alphaBits);
		super(8 + alphaBits, alphaBits == 0 ? new int[] { 8, 8, 8 } : new int[] { 8, 8,
				8, alphaBits }, ColorSpace
				.getInstance(ColorSpace.CS_LINEAR_RGB), alphaBits != 0, false,
						resolveTransparency(alphaBits), DataBuffer.TYPE_BYTE);

		indexedColorModel = new DirectColorModel(
				ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB),
				24 + alphaBits, 0x00FF0000, 0x0000FF00, 0x000000FF,
				(1 << alphaBits) - 1 << 24, false, DataBuffer.TYPE_INT);
		
		// validate arguments
		if (!BLPEncodingType.INDEXED.isAlphaBitsValid(alphaBits))
			throw new IllegalArgumentException(
					"Unsupported alphaBits.");
		else if(cmap.length != INDEXED_PALETTE_SIZE) {
			throw new IllegalArgumentException(
					String.format("Parameter cmap must have exactly %d indices.", INDEXED_PALETTE_SIZE));
		}

		colorMap = cmap.clone();
	}

	/**
	 * Convert a index into a BLP color pixel.
	 * 
	 * @param index
	 *            color index requested.
	 * @return the BLP Direct color.
	 */
	private int getIndexedColor(byte index) {
		return colorMap[index & 0xFF] & 0xFFFFFF;
	}

	/**
	 * Populates the color component cache used to help select which color index
	 * to use to represent a color. This cache is populated only when converting
	 * from components to a pixel.
	 */
	private void populateComponentCache() {
		// only initalize once, this is expensive
		if (normalizedComponentCache != null)
			return;

		int nComponents = indexedColorModel.getNumColorComponents();
		normalizedComponentCache = new float[colorMap.length * nComponents];

		int[] componentCache = new int[4];
		for (int i = 0; i < colorMap.length; i += 1) {
			int offset = i * nComponents;

			// normalize pixel
			int pixel = getIndexedColor((byte) i);
			indexedColorModel.getNormalizedComponents(
					indexedColorModel.getComponents(pixel, componentCache, 0), 0,
					normalizedComponentCache, offset);

			// translate color components to sRGB
			float[] lrgbComponents = Arrays.copyOfRange(
					normalizedComponentCache, offset, offset + nComponents);
			float[] srgbComponents = indexedColorModel.getColorSpace().toRGB(
					lrgbComponents);
			System.arraycopy(srgbComponents, 0, normalizedComponentCache,
					offset, nComponents);
		}
	}

	public int getBandNumber() {
		return hasAlpha() ? 2 : 1;
	}

	@Override
	public int[] getComponents(int pixel, int[] components, int offset) {
		// decode color index
		components = indexedColorModel.getComponents(
				getIndexedColor((byte) (pixel & 0xFF)), components, offset);

		// apply separate alpha
		if (hasAlpha())
			components[offset + 3] = (pixel >> 8) & 0xFF;

		return components;
	}

	@Override
	public int[] getComponents(Object pixel, int[] components, int offset) {
		byte[] bytepixel = (byte[]) pixel;

		// decode color index
		components = indexedColorModel.getComponents(
				getIndexedColor((byte) (bytepixel[0] & 0xFF)), components,
				offset);

		// apply separate alpha
		if (hasAlpha())
			components[offset + 3] = (int) bytepixel[1] & 0xFF;

		return components;
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
	private byte getBestIndex(float[] normComponents, int normOffset) {
		// need color cache
		populateComponentCache();

		// translate color components to sRGB
		float[] lrgbComponents = Arrays.copyOfRange(normComponents, normOffset,
				normOffset + 3);
		float[] srgbComponents = indexedColorModel.getColorSpace().toRGB(
				lrgbComponents);

		// best result
		int best = -1;
		int nComponents = indexedColorModel.getNumColorComponents();
		float bestDiff = Float.MAX_VALUE;

		// for each
		for (int i = 0; i < colorMap.length; i += 1) {
			int cacheOffset = i * nComponents;

			// compare color channels using euclidian distance
			float diff = 0f;
			for (int component = 0; component < nComponents; component += 1) {
				float delta = normalizedComponentCache[cacheOffset + component]
						- srgbComponents[component];
				diff += delta * delta;
			}
			diff = (float) Math.sqrt(diff);

			// find best result
			if (diff < bestDiff) {
				best = i;
				bestDiff = diff;
			}
		}

		return (byte) best;
	}

	@Override
	public int getDataElement(float[] normComponents, int normOffset) {
		int pixel = getBestIndex(normComponents, normOffset) & 0xFF;

		if (hasAlpha())
			pixel |= ((indexedColorModel.getDataElement(normComponents, normOffset) >> 24) & 0xFF) << 8;

		return pixel;
	}

	@Override
	public int getDataElement(int[] components, int offset) {
		int pixel = getBestIndex(indexedColorModel.getNormalizedComponents(
				components, offset, null, 0), 0) & 0xFF;

		if (hasAlpha())
			pixel |= components[offset + 3] << 8;

		return pixel;
	}

	@Override
	public Object getDataElements(int rgb, Object pixel) {
		if (pixel == null) {
			pixel = new byte[getBandNumber()];
		}
		byte[] bytepixel = (byte[]) pixel;

		Object rgbpixel = indexedColorModel.getDataElements(rgb, null);

		bytepixel[0] = getBestIndex(
				indexedColorModel.getNormalizedComponents(rgbpixel, null, 0), 0);

		if (hasAlpha())
			bytepixel[1] = (byte) indexedColorModel
					.getComponents(rgbpixel, null, 0)[getNumColorComponents()];

		return pixel;
	}

	@Override
	public Object getDataElements(float[] normComponents, int normOffset,
			Object obj) {
		if (obj == null) {
			obj = new byte[getBandNumber()];
		}
		byte[] bytepixel = (byte[]) obj;

		bytepixel[0] = getBestIndex(normComponents, normOffset);

		if (hasAlpha())
			bytepixel[1] = (byte) (indexedColorModel.getUnnormalizedComponents(
					normComponents, normOffset, null, 0)[getNumColorComponents()]);

		return obj;
	}

	@Override
	public Object getDataElements(int[] components, int offset, Object obj) {
		if (obj == null) {
			obj = new byte[getBandNumber()];
		}
		byte[] bytepixel = (byte[]) obj;

		bytepixel[0] = getBestIndex(indexedColorModel.getNormalizedComponents(
				components, offset, null, 0), 0);

		if (hasAlpha())
			bytepixel[1] = (byte) components[getNumColorComponents()];

		return obj;
	}

	@Override
	public int getRed(Object inData) {
		byte[] bytepixel = (byte[]) inData;
		return indexedColorModel.getRed(getIndexedColor(bytepixel[0]));
	}

	@Override
	public int getGreen(Object inData) {
		byte[] bytepixel = (byte[]) inData;
		return indexedColorModel.getGreen(getIndexedColor(bytepixel[0]));
	}

	@Override
	public int getBlue(Object inData) {
		byte[] bytepixel = (byte[]) inData;
		return indexedColorModel.getBlue(getIndexedColor(bytepixel[0]));
	}

	@Override
	public int getAlpha(Object inData) {
		// always opaque if no alpha
		if (!hasAlpha())
			return 255;

		byte[] bytepixel = (byte[]) inData;
		return indexedColorModel.getAlpha((bytepixel[1] & 0xFF) << 24);
	}

	@Override
	public int getRGB(Object inData) {
		byte[] bytepixel = (byte[]) inData;
		int pixel = getIndexedColor(bytepixel[0]);
		if (hasAlpha())
			pixel |= ((bytepixel[1] & 0xFF) << 24);

		return indexedColorModel.getRGB(pixel);
	}

	@Override
	public int getRed(int pixel) {
		return indexedColorModel.getRed(getIndexedColor((byte) (pixel & 0xFF)));
	}

	@Override
	public int getGreen(int pixel) {
		return indexedColorModel.getGreen(getIndexedColor((byte) (pixel & 0xFF)));
	}

	@Override
	public int getBlue(int pixel) {
		return indexedColorModel.getBlue(getIndexedColor((byte) (pixel & 0xFF)));
	}

	@Override
	public int getAlpha(int pixel) {
		// always opaque if no alpha
		if (!hasAlpha())
			return 255;

		return indexedColorModel.getAlpha(((pixel >> 8) & 0xFF) << 24);
	}

	@Override
	public int getRGB(int pixel) {
		int pixelBLPD = getIndexedColor((byte) (pixel & 0xFF));
		if (hasAlpha())
			pixelBLPD |= (((pixel >> 8) & 0xFF) << 24);

		return indexedColorModel.getRGB(pixelBLPD);
	}

	@Override
	public WritableRaster getAlphaRaster(WritableRaster raster) {
		if (hasAlpha())
			return raster.createWritableChild(raster.getMinX(),
					raster.getMinY(), raster.getWidth(), raster.getHeight(),
					raster.getMinX(), raster.getMinY(), new int[] { 1 });
		else
			return super.getAlphaRaster(raster);
	}

	@Override
	public WritableRaster createCompatibleWritableRaster(int w, int h) {
		SampleModel sm = createCompatibleSampleModel(w, h);
		return Raster.createWritableRaster(sm, sm.createDataBuffer(), null);
	}

	@Override
	public boolean isCompatibleRaster(Raster raster) {
		return isCompatibleSampleModel(raster.getSampleModel())
				&& raster.getNumBands() == getBandNumber();
	}

	@Override
	public SampleModel createCompatibleSampleModel(int w, int h) {
		return new BLPIndexSampleModel(w, h,
				indexedColorModel.getComponentSize(getNumColorComponents()));
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
		if (sm.getSampleSize(0) != 8)
			return false;
		// check alpha band size
		if (hasAlpha()
				&& sm.getSampleSize(1) != getComponentSize(getNumColorComponents()))
			return false;

		return true;
	}
	
	public int[] getColorMap() {
		return colorMap.clone();
	}

}
