package com.hiveworkshop.blizzard.blp;

import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.event.IIOReadWarningListener;
import javax.imageio.event.IIOWriteWarningListener;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import com.hiveworkshop.lang.LocalizedText;

/**
 * Mipmap processor for JPEG content BLP files.
 * <p>
 * In the case that a decoded JPEG image is not the correct size, it is resized
 * and a warning generated. Resizing occurs by padding/cropping the right and
 * bottom edges of the image. Padding is transparent black.
 * <p>
 * Some poor BLP implementations, such as used by Warcraft III, do not read and
 * process mipmap data safely so might be able to extract a valid JPEG file from
 * a technically corrupt file.
 * <p>
 * Both 8 and 0 bit alpha is supported. A fully opaque alpha band is encoded
 * when set to 0 bits. When decoding 0 bit alpha and using a deep scan a warning
 * is generated if the alpha channel is not fully opaque. Some poor BLP
 * implementations, such as used by Warcraft III, can still process the dummy
 * alpha band which can result in undesirable visual artifacts depending on use.
 * 
 * @author Imperial Good
 */
class JPEGMipmapProcessor extends MipmapProcessor {
	/**
	 * The maximum valid shared header length.
	 * <p>
	 * Shared headers beyond this size might cause massive image corruption or
	 * crashes in some readers.
	 */
	private static final int MAX_SHARED_HEADER_LENGTH = 0x270;

	/**
	 * BLP JPEG content band mapping array.
	 * <p>
	 * This represents legacy windows color order of BGRA. Since only the B and R
	 * samples are swapped one can use this to both encode and decode.
	 */
	private static final int[] JPEG_BAND_ARRAY = { 2, 1, 0, 3 };

	/**
	 * The initial size of the buffer used to hold written JPEG images.
	 * <p>
	 * A value of 128kB was chosen because it is large enough for encoding most
	 * 512x512 images without having to be resized.
	 */
	private static final int INITIAL_ENCODE_BUFFER_SIZE = 128 << 10;

	/**
	 * Alpha value for opaque sample.
	 */
	private static final int OPAQUE_SAMPLE = (1 << Byte.SIZE) - 1;

	/**
	 * If alpha can be encoded.
	 */
	private final boolean hasAlpha;

	/**
	 * The color model that the processor will use.
	 */
	private ColorModel jpegBLPColorModel = null;

	/**
	 * JPEG header block.
	 */
	private byte[] jpegHeader = null;

	/**
	 * Constructs a MipmapProcessor for JPEG content.
	 * 
	 * @param alphaBits
	 *            The alpha component bits, if any.
	 * @param listener
	 *            Warning listener to use.
	 * @throws IllegalArgumentException
	 *             If alphaBits is not valid.
	 */
	public JPEGMipmapProcessor(final int alphaBits, final WarningListener listener) {
		super(listener);
		if (!BLPEncodingType.JPEG.isAlphaBitsValid(alphaBits))
			throw new IllegalArgumentException("Unsupported alphaBits.");
		hasAlpha = alphaBits == 8;
	}

	@Override
	public boolean canUseRaster(final Raster mipmapRaster) {
		final var sampleModel = mipmapRaster.getSampleModel();

		if (sampleModel.getNumBands() != JPEG_BAND_ARRAY.length || sampleModel.getDataType() != DataBuffer.TYPE_BYTE) {
			return false;
		}

		for (var band = 0; band < sampleModel.getNumBands(); band += 1) {
			final var sampleSize = sampleModel.getSampleSize(band);
			if (sampleSize != Byte.SIZE) {
				return false;
			}
		}

		return true;
	}

	@Override
	public Raster decodeMipmapToRaster(final byte[] mipmapData, final int mipmapIndex) throws IOException {
		// Create a buffered JPEG file.
		final var jpegBuffer = Arrays.copyOf(jpegHeader, jpegHeader.length + mipmapData.length);
		System.arraycopy(mipmapData, 0, jpegBuffer, jpegHeader.length, mipmapData.length);

		// Initialize JPEG reader.
		final var jpegReader = getJPEGReader();
		jpegReader.addIIOReadWarningListener(new IIOReadWarningListener() {
			@Override
			public void warningOccurred(final ImageReader source, final String warning) {
				listener.sendWarning(new LocalizedText<BLPText>(BLPText.JPEG_WARNING,
						source.getOriginatingProvider().getVendorName(), warning));
			}
		});

		// Read Raster.
		final Raster jpegRaster;
		try (final var bis = new ByteArrayInputStream(jpegBuffer);
				final var iis = new MemoryCacheImageInputStream(bis)) {
			jpegReader.setInput(iis, true, true);
			jpegRaster = jpegReader.readRaster(0, null);
		} finally {
			jpegReader.dispose();
		}

		return jpegRaster;
	}

	@Override
	public List<byte[]> encodeRastersToMipmaps(List<Raster> mipmapRasters, ImageWriteParam param) throws IOException {
		if (!isMipmapRasterListValid(mipmapRasters)) {
			throw new IllegalArgumentException("Invalid mipmap rasters list.");
		}

		// Initialize JPEG writer.
		final var jpegWriter = getJPEGWriter();
		final var jpegParam = jpegWriter.getDefaultWriteParam();

		/*
		 * final String[] compressionTypes = jpegParam.getCompressionTypes(); if
		 * (compressionTypes != null && compressionTypes.length > 0) {
		 * jpegParam.setCompressionType(compressionTypes[0]); }
		 */
		if (jpegParam.canWriteCompressed()) {
			jpegParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			final var explicitCompression = param != null && param.canWriteCompressed()
					&& param.getCompressionMode() == ImageWriteParam.MODE_EXPLICIT;
			final var compressionQuality = explicitCompression ? param.getCompressionQuality()
					: BLPWriteParam.DEFAULT_QUALITY;
			jpegParam.setCompressionQuality(compressionQuality);
		}
		jpegWriter.addIIOWriteWarningListener(new IIOWriteWarningListener() {
			@Override
			public void warningOccurred(ImageWriter source, int imageIndex, String warning) {
				listener.sendWarning(new LocalizedText<BLPText>(BLPText.JPEG_WARNING,
						source.getOriginatingProvider().getVendorName(), warning));
			}
		});

		// Encode all mipmap rasters into mipmap data.
		final var mipmapDataList = new ArrayList<byte[]>(mipmapRasters.size());
		try (final var bos = new ByteArrayOutputStream(INITIAL_ENCODE_BUFFER_SIZE)) {
			for (final var mipmapRaster : mipmapRasters) {
				bos.reset();
				try (final var ios = new MemoryCacheImageOutputStream(bos)) {
					jpegWriter.setOutput(ios);
					jpegWriter.write(null, new IIOImage(mipmapRaster, null, null), jpegParam);
				}
				mipmapDataList.add(bos.toByteArray());
			}
		} finally {
			jpegWriter.dispose();
		}

		// Resolve length of shared header.
		final var mipmapDataIterator = mipmapDataList.iterator();
		var prev = mipmapDataIterator.next();
		var sharedHeaderLength = prev.length;
		while (mipmapDataIterator.hasNext()) {
			final var next = mipmapDataIterator.next();
			var sharedLength = Arrays.mismatch(prev, 0, sharedHeaderLength, next, 0, sharedHeaderLength);
			if (sharedLength == -1) {
				sharedLength = Math.min(prev.length, next.length);
			}
			sharedHeaderLength = Math.min(sharedHeaderLength, sharedLength);
		}
		sharedHeaderLength = Math.min(sharedHeaderLength, MAX_SHARED_HEADER_LENGTH);

		// Generate compressed mipmapData and shared header data.
		final var compressedDataList = new ArrayList<byte[]>(mipmapRasters.size());
		for (final var mipmapData : mipmapDataList) {
			final var compressedData = Arrays.copyOfRange(mipmapData, sharedHeaderLength, mipmapData.length);
			compressedDataList.add(compressedData);
		}
		jpegHeader = Arrays.copyOf(prev, sharedHeaderLength);

		return compressedDataList;
	}

	@Override
	public BufferedImage generateMipmapImage(final Raster mipmapRaster, final int mipmapIndex) throws IOException {
		var deepCheck = this.deepCheck;

		// Check if raster is malformed.
		final var width = getWidth(mipmapIndex);
		final var height = getHeight(mipmapIndex);
		final var malformedRaster = mipmapRaster.getWidth() != width || mipmapRaster.getHeight() != height
				|| mipmapRaster.getNumBands() != JPEG_BAND_ARRAY.length;
		if (malformedRaster) {
			listener.sendWarning(new LocalizedText<BLPText>(BLPText.BAD_MIPMAP_DIMENSION, mipmapRaster.getNumBands(),
					mipmapRaster.getWidth(), mipmapRaster.getHeight(), JPEG_BAND_ARRAY.length, width, height));

			// Deep check as the raster is malformed.
			deepCheck = true;
		}

		final WritableRaster backingWritableRaster;
		if (!(mipmapRaster instanceof WritableRaster) || malformedRaster) {
			// Generate a new writable raster and copy as much as possible.
			backingWritableRaster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, width, height,
					JPEG_BAND_ARRAY.length, null);

			final var minWidth = Math.min(backingWritableRaster.getWidth(), mipmapRaster.getWidth());
			final var minHeight = Math.min(backingWritableRaster.getHeight(), mipmapRaster.getHeight());
			final var minBands = Math.min(backingWritableRaster.getNumBands(), mipmapRaster.getNumBands());
			final var minBandList = new int[minBands];
			for (var band = 0; band < minBandList.length; band += 1) {
				minBandList[band] = band;
			}
			final var sourceRaster = mipmapRaster.createChild(0, 0, minWidth, minHeight, 0, 0, minBandList);
			final var destinationWritableRaster = backingWritableRaster.createWritableChild(0, 0, minWidth, minHeight,
					0, 0, minBandList);

			destinationWritableRaster.setDataElements(0, 0, sourceRaster);
		} else {
			backingWritableRaster = (WritableRaster) mipmapRaster;
		}

		// Deep check alpha band of opaque images.
		if (deepCheck && !hasAlpha) {
			final var alphaBand = JPEG_BAND_ARRAY.length - 1;
			var transparentCount = 0L;
			for (var x = 0; x < width; x += 1) {
				for (var y = 0; y < height; y += 1) {
					if (backingWritableRaster.getSample(x, y, alphaBand) != OPAQUE_SAMPLE) {
						transparentCount += 1;
					}
				}
			}

			if (transparentCount != 0) {
				final var sampleCount = width * height;
				listener.sendWarning(
						new LocalizedText<BLPText>(BLPText.BAD_PIXEL_ALPHA, transparentCount, sampleCount));
			}
		}

		// Construct image.
		final var imageBands = Arrays.copyOf(JPEG_BAND_ARRAY, JPEG_BAND_ARRAY.length - (hasAlpha ? 0 : 1));
		final var imageWritableRaster = backingWritableRaster.createWritableChild(0, 0,
				backingWritableRaster.getWidth(), backingWritableRaster.getHeight(), 0, 0, imageBands);
		final var image = new BufferedImage(getColorModel(), imageWritableRaster, false, null);

		return image;
	}

	/**
	 * Gets a color model appropriate for image content decoded by this processor.
	 * <p>
	 * This color model is cached unless the suggested color space is changed.
	 * 
	 * @return Appropriate color model.
	 */
	private ColorModel getColorModel() {
		if (jpegBLPColorModel != null && !jpegBLPColorModel.getColorSpace().equals(decodeColorSpace)) {
			jpegBLPColorModel = new ComponentColorModel(decodeColorSpace, hasAlpha, false,
					hasAlpha ? Transparency.TRANSLUCENT : Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
		}
		return jpegBLPColorModel;
	}

	/**
	 * Get the image reader to use to decode mipmap data into rasters.
	 * 
	 * @return A JPEG image reader.
	 * @throws IIOException
	 */
	private ImageReader getJPEGReader() throws IIOException {
		// Find a JPEG reader.
		ImageReader jpegReader = null;
		var jpegReaders = ImageIO.getImageReadersByFormatName("jpeg");
		while (jpegReaders.hasNext()) {
			final ImageReader reader = jpegReaders.next();
			if (reader.canReadRaster()) {
				jpegReader = reader;
				break;
			}
		}
		if (jpegReader == null) {
			throw new IIOException("No JPEG ImageReader with Raster support installed.");
		}

		return jpegReader;
	}

	private ImageWriter getJPEGWriter() throws IIOException {
		// Find a JPEG writer.
		ImageWriter jpegWriter = null;
		final var jpegWriters = ImageIO.getImageWritersByFormatName("jpeg");
		while (jpegWriters.hasNext()) {
			final var writer = jpegWriters.next();
			if (writer.canWriteRasters()) {
				jpegWriter = writer;
				break;
			}
		}
		if (jpegWriter == null) {
			throw new IIOException("No JPEG ImageWriter with Raster support installed.");
		}

		return jpegWriter;
	}

	@Override
	public Iterator<ImageTypeSpecifier> getSupportedImageTypes() {
		final var colorModel = getColorModel();
		return Arrays.asList(new ImageTypeSpecifier(colorModel, colorModel.createCompatibleSampleModel(1, 1)))
				.iterator();
	}

	@Override
	public Raster prepareRasterToEncode(final Raster imageRaster) {
		final var canUse = canUseRaster(imageRaster);

		final WritableRaster mipmapWritableRaster;
		if (!(imageRaster instanceof WritableRaster) || !canUse) {
			// Generate a new writable raster and copy as much as possible.
			final var width = imageRaster.getWidth();
			final var height = imageRaster.getHeight();
			mipmapWritableRaster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, width, height,
					JPEG_BAND_ARRAY.length, null);

			final var minBands = Math.min(mipmapWritableRaster.getNumBands(), mipmapWritableRaster.getNumBands());
			final var bandList = Arrays.copyOf(JPEG_BAND_ARRAY, minBands);

			final var destinationWritableRaster = mipmapWritableRaster.createWritableChild(0, 0, width, height, 0, 0,
					bandList);

			destinationWritableRaster.setDataElements(0, 0, imageRaster);

			// Fill opaque alpha samples.
			if (!hasAlpha) {
				final var alphaBand = JPEG_BAND_ARRAY.length - 1;
				for (var x = 0; x < width; x += 1) {
					for (var y = 0; y < height; y += 1) {
						mipmapWritableRaster.setSample(x, y, alphaBand, OPAQUE_SAMPLE);
					}
				}
			}
		} else {
			mipmapWritableRaster = (WritableRaster) imageRaster;
		}

		return mipmapWritableRaster;
	}

	@Override
	public void readObject(final ImageInputStream src) throws IOException {
		// Read JPEG header.
		src.setByteOrder(ByteOrder.LITTLE_ENDIAN);
		final var length = src.readInt();

		// Prevent possible memory shortages
		final var available = src.length();
		if (available != -1 && Integer.toUnsignedLong(length) > available - src.getStreamPosition()) {
			throw new EOFException("JPEG mipmap processor extends beyond EOF.");
		}

		final var jpegh = new byte[length];
		src.readFully(jpegh, 0, jpegh.length);

		// Length warning.
		if (length > MAX_SHARED_HEADER_LENGTH) {
			listener.sendWarning(new LocalizedText<BLPText>(BLPText.BAD_JPEG_HEADER, length, MAX_SHARED_HEADER_LENGTH));
		}

		jpegHeader = jpegh;
	}

	@Override
	public void writeObject(final ImageOutputStream dst) throws IOException {
		if (jpegHeader == null) {
			throw new IllegalStateException("Cannot write JPEG mipmap processor as no mipmaps have been processed.");
		}

		// write JPEG header
		dst.setByteOrder(ByteOrder.LITTLE_ENDIAN);
		dst.writeInt(jpegHeader.length);
		dst.write(jpegHeader);
	}
}
