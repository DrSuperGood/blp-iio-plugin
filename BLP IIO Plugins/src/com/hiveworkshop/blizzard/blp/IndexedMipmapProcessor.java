package com.hiveworkshop.blizzard.blp;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import com.hiveworkshop.lang.LocalizedText;

/**
 * A class that is responsible for processing between mipmap data and indexed
 * color content.
 * <p>
 * During decoding if the mipmap data is of incorrect size then it is resized to
 * fit and a warning is generated. Some poor BLP implementations, such as used
 * by some versions of Warcraft III, do not read and process mipmap data safely
 * so might be able to extract more meaningful visual information from a
 * technically corrupt file.
 * 
 * @author Imperial Good
 */
public class IndexedMipmapProcessor extends MipmapProcessor {
	/**
	 * The bad size array used by the sample model..
	 */
	private final int[] bandSizes;

	/**
	 * The color palette array for the color model to use.
	 */
	private int[] palette;

	/**
	 * The alpha band precision to use.
	 */
	private final int alphaBits;

	/**
	 * Constructs a MipmapProcessor for indexed color content.
	 * 
	 * @param alphaBits
	 *            the alpha component bits, if any.
	 * @throws IllegalArgumentException
	 *             if alphaBits is not valid.
	 */
	public IndexedMipmapProcessor(final int alphaBits, final WarningListener listener) {
		super(listener);
		if (!BLPEncodingType.INDEXED.isAlphaBitsValid(alphaBits))
			throw new IllegalArgumentException("Unsupported alphaBits.");
		this.alphaBits = alphaBits;
		bandSizes = alphaBits != 0 ? new int[] { BLPIndexColorModel.PALETTE_INDEX_BITS, alphaBits }
				: new int[] { BLPIndexColorModel.PALETTE_INDEX_BITS };
	}

	@Override
	public boolean canUseRaster(final Raster raster) {
		final var sampleModel = raster.getSampleModel();
		final var sampleSizes = sampleModel.getSampleSize();
		return (sampleModel instanceof BLPPackedSampleModel) && Arrays.equals(sampleSizes, bandSizes);
	}

	@Override
	public Raster decodeMipmapToRaster(final byte[] mipmapData, final int mipmapIndex) throws IOException {
		// Produce image WritableRaster
		final var width = getWidth(mipmapIndex);
		final var height = getHeight(mipmapIndex);
		final var sampleModel = new BLPPackedSampleModel(width, height, bandSizes, null);
		final var dataBuffer = new DataBufferByte(mipmapData, mipmapData.length);
		final var writableRaster = Raster.createWritableRaster(sampleModel, dataBuffer, null);

		return writableRaster;
	}

	@Override
	public List<byte[]> encodeRastersToMipmaps(final List<Raster> mipmapRasters, final ImageWriteParam param)
			throws IOException {
		if (!isMipmapRasterListValid(mipmapRasters)) {
			throw new IllegalArgumentException("Invalid mipmap rasters list.");
		}

		// Encode all mipmap rasters into mipmap data.
		final var mipmapDataList = new ArrayList<byte[]>(mipmapRasters.size());
		for (final var mipmapRaster : mipmapRasters) {
			final var dataBuffer = (DataBufferByte) mipmapRaster.getDataBuffer();
			final var mipmapData = dataBuffer.getData().clone();
			mipmapDataList.add(mipmapData);
		}

		return mipmapDataList;
	}

	@Override
	public BufferedImage generateMipmapImage(final Raster mipmapRaster, final int mipmapIndex) throws IOException {
		if (palette == null) {
			throw new IllegalStateException("Cannot generate mipmap image as no palette has been loaded.");
		}

		final var sampleModel = (BLPPackedSampleModel) mipmapRaster.getSampleModel();
		final var dataBuffer = (DataBufferByte) mipmapRaster.getDataBuffer();
		final var dataBufferSize = dataBuffer.getSize();
		final var expectedDataBufferSize = sampleModel.getBufferSize();

		// Sample model is always correct, but the data buffer size might not be.
		final var malformedRaster = dataBufferSize != expectedDataBufferSize;
		if (malformedRaster) {
			listener.sendWarning(
					new LocalizedText<BLPText>(BLPText.BAD_DATA_BUFFER, dataBufferSize, expectedDataBufferSize));
		}

		final WritableRaster backingWritableRaster;
		if (!(mipmapRaster instanceof WritableRaster) || malformedRaster) {
			// Generate a new writable raster and copy as much as possible.
			final var backingDataArray = Arrays.copyOf(dataBuffer.getData(), expectedDataBufferSize);
			final var backingDataBuffer = new DataBufferByte(backingDataArray, backingDataArray.length);
			backingWritableRaster = Raster.createWritableRaster(sampleModel, backingDataBuffer, null);
		} else {
			backingWritableRaster = (WritableRaster) mipmapRaster;
		}

		// Produce mipmap image.
		final var mipmapImage = new BufferedImage(getColorModel(), backingWritableRaster, false, null);

		return mipmapImage;
	}

	/**
	 * Get the color model used to decode or encode images with this processor.
	 * 
	 * @return The processor color model.
	 */
	private BLPIndexColorModel getColorModel() {
		final var colorModel = new BLPIndexColorModel(palette, alphaBits, decodeColorSpace);

		return colorModel;
	}

	@Override
	public Iterator<ImageTypeSpecifier> getSupportedImageTypes() {
		final var colorModel = getColorModel();
		final var sampleModel = new BLPPackedSampleModel(1, 1, bandSizes, null);
		final var imageTypeSpecifier = new ImageTypeSpecifier(colorModel, sampleModel);
		return List.of(imageTypeSpecifier).iterator();
	}

	@Override
	public Raster prepareRasterToEncode(final Raster imageRaster) {
		final var sampleModel = imageRaster.getSampleModel();

		final Raster mipmapRaster;
		if (!(sampleModel instanceof BLPPackedSampleModel) || !Arrays.equals(sampleModel.getSampleSize(), bandSizes)) {
			// Build new raster, re-scaling samples as required.
			final var width = imageRaster.getWidth();
			final var height = imageRaster.getHeight();
			final var backingSampleModel = new BLPPackedSampleModel(width, height, bandSizes, null);
			final var backingDataBuffer = backingSampleModel.createDataBuffer();
			final var backingWritableRaster = Raster.createWritableRaster(backingSampleModel, backingDataBuffer, null);

			final var minBandCount = Math.min(sampleModel.getNumBands(), backingSampleModel.getNumBands());
			for (var band = 0; band < minBandCount; band += 1) {
				final var sampleSize = sampleModel.getSampleSize(band);
				final var backingSampleSize = backingSampleModel.getSampleSize(band);
				final double unity = (1 << sampleSize) - 1;
				final double backingUnity = (1 << backingSampleSize) - 1;
				for (var x = 0; x < width; x += 1) {
					for (var y = 0; y < height; y += 1) {
						final var sample = imageRaster.getSample(x, y, band);

						final int backingSample;
						if (sampleSize != backingSampleSize) {
							final var normalSample = sample / unity;
							backingSample = (int) (normalSample * backingUnity);
						} else {
							backingSample = sample;
						}

						backingWritableRaster.setSample(x, y, band, backingSample);
					}
				}
			}

			mipmapRaster = backingWritableRaster;
		} else {
			mipmapRaster = imageRaster;
		}

		return mipmapRaster;
	}

	@Override
	public void readObject(final ImageInputStream src) throws IOException {
		src.setByteOrder(ByteOrder.LITTLE_ENDIAN);
		final var palette = new int[BLPIndexColorModel.MAX_PALETTE_LENGTH];
		src.readFully(palette, 0, palette.length);

		this.palette = palette;
	}

	/**
	 * Set the palette used to decode mipmap images.
	 * <p>
	 * The palette must contain at most
	 * <code>BLPIndexColorModel.MAX_PALETTE_LENGTH</code> elements. These elements
	 * represent colors using a color model generated by
	 * <code>BLPIndexColorModel.createPaletteColorModel(colorSpace)</code>.
	 * 
	 * @param palette
	 *            Palette array to use.
	 */
	public void setPalette(final int[] palette) {
		if (palette != null) {
			if (palette.length > BLPIndexColorModel.MAX_PALETTE_LENGTH) {
				throw new IllegalArgumentException("Trying to set a palette larger than MAX_PALETTE_LENGTH.");
			}
			this.palette = Arrays.copyOf(palette, BLPIndexColorModel.MAX_PALETTE_LENGTH);
		} else {
			this.palette = null;
		}
	}

	@Override
	public void writeObject(final ImageOutputStream dst) throws IOException {
		if (palette == null) {
			throw new IllegalStateException("Cannot write indexed mipmap processor as no palette has been loaded.");
		}

		dst.setByteOrder(ByteOrder.LITTLE_ENDIAN);
		dst.writeInts(palette, 0, palette.length);
	}

}
