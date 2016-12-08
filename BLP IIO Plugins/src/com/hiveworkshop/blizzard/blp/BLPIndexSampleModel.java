package com.hiveworkshop.blizzard.blp;

import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.SampleModel;
import java.util.Arrays;

/**
 * SampleModel for BLPIndexColorModel. First band acts like a
 * ComponentSampleModel for pixel color index. Second band acts a like
 * non-standard MultiPixelPackedSampleModel for pixel alpha. Bands are stored
 * sequentially.
 * <p>
 * The alphaBits value determine alpha precision and should match alphaBits of
 * BLPColorModel. When 0 there is only a single band, otherwise there are 2
 * bands. Valid alphaBits values are 1 (masking), 4 (low precision) and 8 (full
 * precision).
 * 
 * @author ImperialGood
 */
public class BLPIndexSampleModel extends SampleModel {
	/**
	 * Band sizes array.
	 */
	private final int[] bandSizes;

	/**
	 * Alpha channel bit precision.
	 */
	private final int alphaBits;

	/**
	 * Constructs a SampleModel for the given dimension with the specified alpha
	 * precision.
	 * <p>
	 * The alphaBits field only supports 0, 1, 4 and 8. Anything else will throw
	 * an IllegalArgumentException.
	 * <p>
	 * Both w and h have to be at least 1. Anything else will throw an
	 * IllegalArgumentException.
	 * 
	 * @param w
	 *            width in pixels.
	 * @param h
	 *            height in pixels.
	 * @param alphaBits
	 *            bit precision of the alpha channel.
	 * @throws IllegalArgumentException
	 *             if w, h or alphaBits have an illegal value.
	 */
	public BLPIndexSampleModel(int w, int h, int alphaBits) {
		super(DataBuffer.TYPE_BYTE, w, h, alphaBits == 0 ? 1 : 2);

		// validate arguments
		if (!BLPEncodingType.INDEXED.isAlphaBitsValid(alphaBits))
			throw new IllegalArgumentException(
					"illegal alphaBits (0, 1, 4 or 8 legal)");
		if (w < 1 || h < 1)
			throw new IllegalArgumentException("illegal dimensions");

		this.alphaBits = alphaBits;
		if (alphaBits == 0)
			bandSizes = new int[] { 8 };
		else
			bandSizes = new int[] { 8, alphaBits };
	}

	private int getElementNumber(int x, int y) {
		return x + width * y;
	}

	private int getSampleIndex(int x, int y, DataBuffer data) {
		return data.getElem(getElementNumber(x, y));
	}

	private void setSampleIndex(int x, int y, int s, DataBuffer data) {
		data.setElem(getElementNumber(x, y), s);
	}

	private int getElementNumberAlpha(int elementNumber) {
		return width * height + elementNumber * alphaBits / 8;
	}

	private int getSampleOffsetAlpha(int elementNumber) {
		return (elementNumber % (8 / alphaBits)) * alphaBits;
	}

	private int getSampleMaskAlpha() {
		return (1 << alphaBits) - 1;
	}

	private int getSampleAlpha(int x, int y, DataBuffer data) {
		int offset = getElementNumber(x, y);
		return data.getElem(getElementNumberAlpha(offset)) >> getSampleOffsetAlpha(offset)
				& getSampleMaskAlpha();
	}

	private void setSampleAlpha(int x, int y, int s, DataBuffer data) {
		int offset = getElementNumber(x, y);
		int element = getElementNumberAlpha(offset);
		int sampleOffset = getSampleOffsetAlpha(offset);
		int mask = getSampleMaskAlpha();
		data.setElem(element, data.getElem(element) & ~(mask << sampleOffset)
				| (s & mask) << sampleOffset);
	}

	@Override
	public int getNumDataElements() {
		return bandSizes.length;
	}

	@Override
	public Object getDataElements(int x, int y, Object obj, DataBuffer data) {
		// process obj
		if (obj == null) {
			obj = new byte[numBands];
		}
		byte[] pixel = (byte[]) (obj);

		// get pixel
		pixel[0] = (byte) getSampleIndex(x, y, data);
		if (numBands == 2)
			pixel[1] = (byte) getSampleAlpha(x, y, data);

		return obj;
	}

	@Override
	public void setDataElements(int x, int y, Object obj, DataBuffer data) {
		// process obj
		byte[] pixel = (byte[]) (obj);

		// set pixel
		setSampleIndex(x, y, pixel[0] & 0xFF, data);
		if (numBands == 2)
			setSampleAlpha(x, y, pixel[1] & 0xFF, data);
	}

	@Override
	public int getSample(int x, int y, int b, DataBuffer data) {
		if (b == 0)
			return getSampleIndex(x, y, data);
		else if (b == 1)
			return getSampleIndex(x, y, data);
		throw new IllegalArgumentException("illegal band (0 or 1 legal)");
	}

	@Override
	public void setSample(int x, int y, int b, int s, DataBuffer data) {
		if (b == 0)
			setSampleIndex(x, y, s, data);
		else if (b == 1)
			setSampleAlpha(x, y, s, data);
		throw new IllegalArgumentException("illegal band (0 or 1 legal)");
	}

	@Override
	public BLPIndexSampleModel createCompatibleSampleModel(int w, int h) {
		return new BLPIndexSampleModel(w, h, alphaBits);
	}

	@Override
	public SampleModel createSubsetSampleModel(int[] bands) {
		if (bands.length != numBands)
			throw new IllegalArgumentException("too many bands");
		else if (bands[0] != 0 || (numBands == 2 && bands[1] != 1))
			throw new IllegalArgumentException("band redirection not supported");
		return this;
	}

	public int getExpectedDataBufferSize() {
		return (width * height * (8 + alphaBits) + 7) / 8;
	}

	@Override
	public DataBuffer createDataBuffer() {
		return new DataBufferByte(getExpectedDataBufferSize());
	}

	@Override
	public int[] getSampleSize() {
		return Arrays.copyOf(bandSizes, bandSizes.length);
	}

	@Override
	public int getSampleSize(int band) {
		return bandSizes[band];
	}

}
