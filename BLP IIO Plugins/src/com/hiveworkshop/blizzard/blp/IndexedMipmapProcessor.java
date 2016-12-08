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

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import com.hiveworkshop.lang.LocalizedFormatedString;

import static com.hiveworkshop.blizzard.blp.BLPCommon.INDEXED_PALETTE_SIZE;

/**
 * 
 * @author Imperial Good
 */
public class IndexedMipmapProcessor extends MipmapProcessor {
	private static final BLPIndexColorModel DEFAULT_INDEX_COLOR_MODEL = new BLPIndexColorModel(
			new int[256], 8);

	private BLPIndexColorModel indexedBLPColorModel = DEFAULT_INDEX_COLOR_MODEL;

	private final int alphaBits;

	/**
	 * 
	 */
	public IndexedMipmapProcessor(int alphaBits) {
		this.alphaBits = alphaBits;
	}

	@Override
	public byte[] encodeMipmap(BufferedImage img, ImageWriteParam param,
			Consumer<LocalizedFormatedString> handler) throws IIOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BufferedImage decodeMipmap(byte[] mmData, ImageReadParam param,
			int width, int height, Consumer<LocalizedFormatedString> handler)
			throws IIOException {
		// create sample model
		final BLPIndexSampleModel sm = new BLPIndexSampleModel(width, height,
				alphaBits);

		// validate chunk size
		int expected = sm.getExpectedDataBufferSize();
		if (mmData.length != expected) {
			handler.accept(new LocalizedFormatedString("com.hiveworkshop.text.blp",
					"BadBuffer", mmData.length, expected));
			mmData = Arrays.copyOf(mmData, expected);
		}

		// produce image WritableRaster
		final DataBuffer db = new DataBufferByte(mmData, expected);
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
						new BLPIndexSampleModel(width, height, alphaBits)))
				.iterator();
	}

	@Override
	public void readObject(ImageInputStream src) throws IOException {
		src.setByteOrder(ByteOrder.LITTLE_ENDIAN);
		int[] cmap = new int[INDEXED_PALETTE_SIZE];
		src.readFully(cmap, 0, cmap.length);

		indexedBLPColorModel = new BLPIndexColorModel(cmap, alphaBits);
	}

	@Override
	public void writeObject(ImageOutputStream dst) throws IOException {
		dst.setByteOrder(ByteOrder.LITTLE_ENDIAN);
		int[] cmap = indexedBLPColorModel.getColorMap();
		dst.writeInts(cmap, 0, cmap.length);
	}

}
