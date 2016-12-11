package com.hiveworkshop.blizzard.blp;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Consumer;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import com.hiveworkshop.lang.LocalizedFormatedString;

import static com.hiveworkshop.blizzard.blp.BLPCommon.INDEXED_PALETTE_SIZE;

/**
 * A class that is responsible for processing between mipmap data and indexed
 * color content.
 * <p>
 * If the mipmap data is of incorrect size then it is resized to fit and a
 * warning is generated.
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
		bandSizes = alphaBits != 0 ? new int[] { 8, alphaBits } : new int[] { 8 };
	}

	@Override
	public byte[] encodeMipmap(BufferedImage img, ImageWriteParam param,
			Consumer<LocalizedFormatedString> handler) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BufferedImage decodeMipmap(byte[] mmData, ImageReadParam param,
			int width, int height, Consumer<LocalizedFormatedString> handler)
			throws IOException {
		// create sample model
		final BLPPackedSampleModel sm = new BLPPackedSampleModel(width, height,
				bandSizes,
				null);

		// validate chunk size
		final int expected = sm.getBufferSize();
		if (mmData.length != expected) {
			handler.accept(new LocalizedFormatedString(
					"com.hiveworkshop.text.blp", "BadBuffer", mmData.length,
					expected));
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
		return Arrays.asList(
				new ImageTypeSpecifier(indexedBLPColorModel,
						new BLPPackedSampleModel(width, height,
								bandSizes,
								null))).iterator();
	}

	@Override
	public void readObject(ImageInputStream src) throws IOException {
		src.setByteOrder(ByteOrder.LITTLE_ENDIAN);
		int[] cmap = new int[INDEXED_PALETTE_SIZE];
		src.readFully(cmap, 0, cmap.length);

		indexedBLPColorModel = new BLPIndexColorModel(cmap, bandSizes.length > 1 ? bandSizes[1] : 0);
		canDecode = true;
	}

	@Override
	public void writeObject(ImageOutputStream dst) throws IOException {
		dst.setByteOrder(ByteOrder.LITTLE_ENDIAN);
		int[] cmap = indexedBLPColorModel.getColorMap();
		dst.writeInts(cmap, 0, cmap.length);
	}

}
