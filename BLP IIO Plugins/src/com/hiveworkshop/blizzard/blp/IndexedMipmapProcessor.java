package com.hiveworkshop.blizzard.blp;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Consumer;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import com.hiveworkshop.lang.LocalizedFormatedString;

/**
 * A class that is responsible for processing between mipmap data and indexed
 * color content.
 * <p>
 * During decoding if the mipmap data is of incorrect size then it is resized to
 * fit and a warning is generated. Some poor BLP implementations, such as used
 * by some versions of Warcraft III, do not read and process mipmap data safely
 * so might be able to extract more meaningful visual information from a
 * technically corrupt file.
 * <p>
 * When encoding images the first image ColorModel is used to determine the
 * color map used. Both BLPIndexColorModel and IndexColorModel are supported
 * although IndexColorModel alpha is not. The direct values of the required
 * bands are then used without further processing. Alpha band is always assumed
 * to be the second band and will be rescaled as required. Missing alpha band
 * will be substituted with opaque pixels if required. Any other bands are
 * discarded.
 * 
 * @author Imperial Good
 */
public class IndexedMipmapProcessor extends MipmapProcessor {
	/**
	 * The BLP indexed color model used to process mipmaps.
	 */
	private BLPIndexColorModel indexedBLPColorModel = null;

	/**
	 * The bandSizes to use.
	 */
	private final int[] bandSizes;

	/**
	 * Constructs a MipmapProcessor for indexed color content.
	 * 
	 * @param alphaBits
	 *            the alpha component bits, if any.
	 * @throws IllegalArgumentException
	 *             if alphaBits is not valid.
	 */
	public IndexedMipmapProcessor(int alphaBits) {
		if (!BLPEncodingType.INDEXED.isAlphaBitsValid(alphaBits))
			throw new IllegalArgumentException("Unsupported alphaBits.");
		bandSizes = alphaBits != 0 ? new int[] { 8, alphaBits }
				: new int[] { 8 };

		// dummy color model
		indexedBLPColorModel = new BLPIndexColorModel(null,
				bandSizes.length > 1 ? bandSizes[1] : 0);
	}

	@Override
	public byte[] encodeMipmap(BufferedImage img, ImageWriteParam param,
			Consumer<LocalizedFormatedString> handler) throws IOException {
		final WritableRaster srcWR = img.getRaster();
		final ColorModel srcCM = img.getColorModel();
		final SampleModel srcSM = srcWR.getSampleModel();
		final int h = srcSM.getHeight();
		final int w = srcSM.getWidth();

		// process ColorModel
		if (!canDecode) {
			// get a color model
			if (srcCM instanceof BLPIndexColorModel) {
				final BLPIndexColorModel blpICM = (BLPIndexColorModel) srcCM;
				indexedBLPColorModel = new BLPIndexColorModel(
						blpICM.getPalette(),
						bandSizes.length > 1 ? bandSizes[1] : 0);
			} else if (srcCM instanceof IndexColorModel) {
				// basic IndexColorModel compatibility
				final IndexColorModel iCM = (IndexColorModel) srcCM;
				final int[] srcCMap = new int[iCM.getMapSize()];
				iCM.getRGBs(srcCMap);

				// color space conversion
				final ColorModel srcCMapCM = ColorModel.getRGBdefault();
				final ColorModel destCMapCM = BLPIndexColorModel.createPaletteColorModel(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB));
				final int[] destCMap = new int[srcCMap.length];
				final int[] components = new int[srcCMapCM
						.getNumColorComponents()];
				for (int i = 0; i < srcCMap.length; i += 1) {
					destCMap[i] = destCMapCM.getDataElement(
							srcCMapCM.getComponents(srcCMap[i], components, 0),
							0);
				}

				indexedBLPColorModel = new BLPIndexColorModel(destCMap,
						bandSizes.length > 1 ? bandSizes[1] : 0);
			} else {
				throw new IIOException(
						"Cannot obtain sensible color map from ColorModel.");
			}
			canDecode = true;
		}

		// create destination
		final SampleModel destSM = new BLPPackedSampleModel(w, h, bandSizes,
				null);
		final DataBuffer destDB = destSM.createDataBuffer();
		final WritableRaster destWR = WritableRaster
				.createWritableRaster(destSM, destDB, null);

		// copy bands
		final boolean hasAlpha = bandSizes.length > 1;
		final boolean srcHasAlpha = hasAlpha && srcSM.getNumBands() > 1;
		final boolean rescaleAlpha = srcHasAlpha
				&& srcSM.getSampleSize(1) != bandSizes[1];
		final int alphaMask = hasAlpha ? (1 << bandSizes[1]) - 1 : 0;
		for (int y = 0; y < h; y += 1) {
			for (int x = 0; x < w; x += 1) {
				destWR.setSample(x, y, 0, srcWR.getSample(x, y, 0));
				if (hasAlpha) {
					if (srcHasAlpha) {
						int alphaSample = srcWR.getSample(x, y, 1);
						if (rescaleAlpha)
							alphaSample = (int) ((float) alphaMask
									* (float) alphaSample
									/ (float) (srcSM.getSampleSize(1) - 1));
						destWR.setSample(x, y, 1, alphaSample);
					} else
						destWR.setSample(x, y, 1, alphaMask);
				}
			}
		}

		// return destination results
		return ((DataBufferByte) srcWR.getDataBuffer()).getData();
	}

	@Override
	public BufferedImage decodeMipmap(byte[] mmData, ImageReadParam param,
			int width, int height, Consumer<LocalizedFormatedString> handler)
			throws IOException {
		// create sample model
		final BLPPackedSampleModel sm = new BLPPackedSampleModel(width, height,
				bandSizes, null);

		// validate chunk size
		final int expected = sm.getBufferSize();
		if (mmData.length != expected) {
			handler.accept(
					new LocalizedFormatedString("com.hiveworkshop.text.blp",
							"BadBuffer", mmData.length, expected));
			mmData = Arrays.copyOf(mmData, expected);
		}

		// produce image WritableRaster
		final DataBuffer db = new DataBufferByte(mmData, mmData.length);
		final WritableRaster raster = Raster.createWritableRaster(sm, db, null);

		// produce buffered image
		BufferedImage img = new BufferedImage(indexedBLPColorModel, raster,
				false, null);

		return img;
	}

	@Override
	public Iterator<ImageTypeSpecifier> getSupportedImageTypes(int width,
			int height) {
		return Arrays.asList(new ImageTypeSpecifier(indexedBLPColorModel,
				new BLPPackedSampleModel(width, height, bandSizes, null)))
				.iterator();
	}

	@Override
	public void readObject(ImageInputStream src,
			Consumer<LocalizedFormatedString> warning) throws IOException {
		src.setByteOrder(ByteOrder.LITTLE_ENDIAN);
		final int[] palette = new int[BLPIndexColorModel.MAX_PALETTE_LENGTH];
		src.readFully(palette, 0, palette.length);

		indexedBLPColorModel = new BLPIndexColorModel(palette,
				bandSizes.length > 1 ? bandSizes[1] : 0);
		canDecode = true;
	}

	@Override
	public void writeObject(ImageOutputStream dst) throws IOException {
		dst.setByteOrder(ByteOrder.LITTLE_ENDIAN);
		final int[] palette = indexedBLPColorModel.getPalette();
		dst.writeInts(palette, 0, palette.length);
	}

}
