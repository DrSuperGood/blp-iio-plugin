package com.hiveworkshop.blizzard.blp;

import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Consumer;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.event.IIOReadWarningListener;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import com.hiveworkshop.lang.LocalizedFormatedString;

/**
 * A class that is responsible for processing between mipmap data and JPEG
 * content.
 * <p>
 * In the case that an encoded JPEG file is not the correct size, it is resized
 * and a warning generated. Resizing occurs on the right and bottom edges of the
 * image. Padding is transparent black.
 * <p>
 * Both 8 and 0 bit alpha is supported. A fully opaque alpha band is encoded
 * when set to 0 bits. When decoding 0 bit alpha and not using direct read a
 * warning is generated if the alpha channel is not fully opaque.
 * <p>
 * The JPEG ImageReader used can be controlled by a BLPReadParam. Likewise the
 * JPEG ImageWriter used can be controlled by a BLPWriteParam. Due to the use of
 * a shared JPEG header between mipmaps to reduce file size the ImageWriter must
 * be kept constant between all images in a BLP file.
 * 
 * @author Imperial Good
 */
class JPEGMipmapProcessor extends MipmapProcessor {
	private static final int[] JPEG_BAND_ARRAY = { 2, 1, 0, 3 };

	/**
	 * The color model that the processor will use.
	 */
	private final ColorModel jpegBLPColorModel;

	/**
	 * JPEG header block.
	 */
	private byte[] jpegHeader = null;

	/**
	 * Constructs a MipmapProcessor for JPEG content.
	 * 
	 * @param alphaBits
	 *            the alpha component bits, if any.
	 * @throws IllegalArgumentException
	 *             if alphaBits is not valid.
	 */
	public JPEGMipmapProcessor(int alphaBits) {
		if (!BLPEncodingType.JPEG.isAlphaBitsValid(alphaBits))
			throw new IllegalArgumentException("Unsupported alphaBits.");
		final boolean hasAlpha = alphaBits == 8;
		jpegBLPColorModel = new ComponentColorModel(
				ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), hasAlpha,
				false, hasAlpha ? Transparency.TRANSLUCENT
						: Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
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
		final boolean directRead = param == null
				|| (param instanceof BLPReadParam && ((BLPReadParam) param)
						.isDirectRead());

		// resolve a JPEG ImageReader
		ImageReader jpegReader = null;
		if (param instanceof BLPReadParam
				&& ((BLPReadParam) param).getJPEGSpi() != null) {
			// use explicit JPEG reader
			jpegReader = ((BLPReadParam) param).getJPEGSpi()
					.createReaderInstance();
		} else {
			// find a JPEG reader
			Iterator<ImageReader> jpegReaders = ImageIO
					.getImageReadersByFormatName("jpeg");
			while (jpegReaders.hasNext()) {
				final ImageReader reader = jpegReaders.next();
				if (reader.canReadRaster()) {
					jpegReader = reader;
					break;
				}
			}
		}
		// validate JPEG reader
		if (jpegReader == null)
			throw new IIOException("No suitable JPEG ImageReader installed.");
		else if (!jpegReader.canReadRaster()) {
			throw new IIOException(String.format(
					"JPEG ImageReader cannot read raster: vendor = %s.",
					jpegReader.getOriginatingProvider().getVendorName()));
		}

		// create a buffered JPEG file in memory
		byte[] jpegBuffer = Arrays.copyOf(jpegHeader, jpegHeader.length
				+ mmData.length);
		System.arraycopy(mmData, 0, jpegBuffer, jpegHeader.length,
				mmData.length);

		// input buffered JPEG file
		InputStream bis = new ByteArrayInputStream(jpegBuffer);
		ImageInputStream iis = new MemoryCacheImageInputStream(bis);
		jpegReader.setInput(iis, true, true);

		// read source raster
		jpegReader.addIIOReadWarningListener(new IIOReadWarningListener() {
			@Override
			public void warningOccurred(ImageReader source, String warning) {
				handler.accept(new LocalizedFormatedString(
						"com.hiveworkshop.text.blp", "JPEGWarning", warning));
			}
		});
		ImageReadParam jpegParam = jpegReader.getDefaultReadParam();
		jpegParam.setSourceBands(JPEG_BAND_ARRAY);
		if (directRead) {
			// optimizations to improve direct read mode performance
			jpegParam.setSourceRegion(new Rectangle(width, height));
		}
		Raster srcRaster = jpegReader.readRaster(0, jpegParam);
		
		// cleanup
		iis.close();
		jpegReader.dispose();

		// direct read shortcut
		if (directRead && srcRaster instanceof WritableRaster
				&& srcRaster.getWidth() == width
				&& srcRaster.getHeight() == height) {
			WritableRaster destRaster = (WritableRaster) srcRaster;

			// enforce alpha band to match color model
			if (!jpegBLPColorModel.hasAlpha())
				destRaster = destRaster.createWritableChild(0, 0,
						destRaster.getWidth(), destRaster.getHeight(), 0, 0,
						new int[] { 0, 1, 2 });

			return new BufferedImage(jpegBLPColorModel, destRaster, false, null);
		}

		// alpha warning check
		if (!jpegBLPColorModel.hasAlpha()) {
			final int[] alphaSamples = srcRaster.getSamples(0, 0,
					srcRaster.getWidth(), srcRaster.getHeight(), 3,
					(int[]) null);
			for (int aSample : alphaSamples) {
				if (aSample != 255) {
					handler.accept(new LocalizedFormatedString(
							"com.hiveworkshop.text.blp", "BadPixelAlpha"));
					break;
				}
			}
		}

		// dimension check warning
		if (srcRaster.getWidth() != width || srcRaster.getHeight() != height)
			handler.accept(new LocalizedFormatedString(
					"com.hiveworkshop.text.blp", "BadDimensions", srcRaster
							.getWidth(), srcRaster.getHeight(), width, height));

		// create destination image
		BufferedImage destImg = new BufferedImage(
				jpegBLPColorModel,
				jpegBLPColorModel.createCompatibleWritableRaster(width, height),
				false, null);
		WritableRaster destRaster = destImg.getRaster();

		// copy data
		destRaster.setRect(srcRaster.createChild(
				0,
				0,
				srcRaster.getWidth(),
				srcRaster.getHeight(),
				0,
				0,
				Arrays.copyOf(new int[] { 0, 1, 2, 3 },
						jpegBLPColorModel.getNumComponents())));

		return destImg;
	}

	@Override
	public Iterator<ImageTypeSpecifier> getSupportedImageTypes(int width,
			int height) {
		return Arrays.asList(
				new ImageTypeSpecifier(jpegBLPColorModel, jpegBLPColorModel
						.createCompatibleSampleModel(width, height)))
				.iterator();
	}

	@Override
	public void readObject(ImageInputStream src) throws IOException {
		// read JPEG header
		src.setByteOrder(ByteOrder.LITTLE_ENDIAN);
		byte[] jpegh = new byte[src.readInt()];
		src.readFully(jpegh, 0, jpegh.length);

		jpegHeader = jpegh;
		canDecode = true;
	}

	@Override
	public void writeObject(ImageOutputStream dst) throws IOException {
		byte[] jpegh = jpegHeader != null ? jpegHeader : new byte[0];

		// write JPEG header
		dst.setByteOrder(ByteOrder.LITTLE_ENDIAN);
		dst.writeInt(jpegh.length);
		dst.write(jpegh);
	}
}
