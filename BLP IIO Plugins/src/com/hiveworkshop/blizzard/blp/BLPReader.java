package com.hiveworkshop.blizzard.blp;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.event.IIOReadWarningListener;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;

import com.hiveworkshop.lang.LocalizedFormatedString;

/**
 * Implementation class for the BLP image reader.
 * <p>
 * Supports opening of BLP versions 0 and 1. Mipmap levels translate into image
 * number.
 * <p>
 * Default resulting BufferedImage objects may come in a variety of image types
 * based on the content of the blp file. The image type chosen aims to preserve
 * the underlying data structure.
 * <p>
 * No image metadata can be extracted to preserve JPEG content image quality.
 * <p>
 * Raster is not supported. Read progress updates are not supported, but all
 * other listeners work.
 * 
 * @author ImperialGood
 */
public class BLPReader extends ImageReader {
	/**
	 * The mipmap index of the full scale image.
	 */
	private final int FULL_IMAGE_MIPMAP_INDEX = 0;

	/**
	 * BLP stream metadata object. Represents the contents of the BLP file header
	 * and is used to decode all mipmap levels.
	 */
	private BLPStreamMetadata streamMeta = null;

	/**
	 * Internally managed ImageInputStream.
	 */
	private ImageInputStream intSrc = null;

	/**
	 * Mipmap manager adapter class. Turns varying manager interfaces into a
	 * standard reader interface.
	 */
	private static abstract class MipmapReader {
		public abstract byte[] getMipmapDataChunk(int mipmap) throws IOException;

		public void flushTo(int mipmap) throws IOException {
		}
	}

	/**
	 * Mipmap reader to get mipmap data chunks from.
	 */
	private MipmapReader mipmapReader;

	/**
	 * Mipmap processor for content.
	 */
	private MipmapProcessor mipmapProcessor = null;

	/**
	 * Controls if BLP mipmap images are read as directly as possible. The value is
	 * set when an image is read using the read param.
	 */
	private boolean direct = false;

	public String tempGetInfo() throws IOException {
		loadHeader();
		return streamMeta.toString();
	}

	public BLPReader(ImageReaderSpi originatingProvider) {
		super(originatingProvider);
	}

	/**
	 * Loads the BLP header from an input source. The header is only loaded once
	 * with the results cached for performance.
	 * 
	 * @throws IOException
	 *             - is header cannot be loaded
	 */
	private void loadHeader() throws IOException {
		// only do something if header has not already been loaded
		if (streamMeta != null)
			return;

		// check if a source has been set
		if (input == null)
			throw new IllegalStateException("no input source has been set");

		// check if input is a file system path
		Path path = null;
		if (input instanceof Path) {
			// directly use path
			path = (Path) input;
		} else if (input instanceof File) {
			// use path of file
			path = ((File) input).toPath();
		}

		// resolve input stream
		ImageInputStream src;
		if (input instanceof ImageInputStream) {
			// ImageInputStream provided
			src = (ImageInputStream) input;
		} else if (path != null) {
			// create internally managed ImageInputStream
			intSrc = new FileImageInputStream(path.toFile());

			// validate Path
			if (intSrc == null)
				throw new IllegalStateException("Cannot create ImageInputStream from path.");
			src = intSrc;
		} else
			// invalid input has been assigned
			throw new IllegalStateException("bad input state");

		// start from beginning of stream
		src.seek(0);

		BLPStreamMetadata streamMeta = new BLPStreamMetadata();
		streamMeta.setWarningHandler(this::processWarningOccurred);
		streamMeta.readObject(src);

		// read mipmap location data
		MipmapReader mipmapReader;
		if (streamMeta.getVersion() > 0) {
			// mipmap chunks within same file
			InternalMipmapManager imm = new InternalMipmapManager();
			imm.readObject(src);
			BLPReader thisref = this;

			mipmapReader = new MipmapReader() {
				@Override
				public byte[] getMipmapDataChunk(int mipmap) throws IOException {
					return imm.getMipmapDataChunk(src, mipmap, thisref::processWarningOccurred);
				}

				@Override
				public void flushTo(int mipmap) throws IOException {
					imm.flushToMipmap(src, mipmap);
				}
			};
		} else if (path != null) {
			// file must have ".blp" extension
			ExternalMipmapManager emm = new ExternalMipmapManager(path);

			mipmapReader = new MipmapReader() {
				@Override
				public byte[] getMipmapDataChunk(int mipmap) throws IOException {
					return emm.getMipmapDataChunk(mipmap);
				}
			};
		} else {
			// no path to locate mipmap chunk files
			throw new IIOException("BLP0 image can only be loaded from Path or File input.");
		}

		// read content header
		if (streamMeta.getEncodingType() == BLPEncodingType.JPEG) {
			mipmapProcessor = new JPEGMipmapProcessor(streamMeta.getAlphaBits());
		} else if (streamMeta.getEncodingType() == BLPEncodingType.INDEXED) {
			mipmapProcessor = new IndexedMipmapProcessor(streamMeta.getAlphaBits());
		} else {
			throw new IIOException("Unsupported content type.");
		}
		mipmapProcessor.readObject(src, this::processWarningOccurred);

		// if seeking forward only then header data can now be discarded
		if (seekForwardOnly)
			mipmapReader.flushTo(0);

		this.streamMeta = streamMeta;
		this.mipmapReader = mipmapReader;
	}

	/**
	 * Checks if the given image index is valid, throwing an exception if not.
	 * 
	 * @param imageIndex
	 *            The image index to check.
	 * @throws IndexOutOfBoundsException
	 *             If the image does not exist.
	 */
	private void checkImageIndex(final int imageIndex) {
		if (imageIndex != 0) {
			throw new IndexOutOfBoundsException(imageIndex);
		}
	}

	/**
	 * Checks if the given thumbnail index is valid, throwing an exception if not.
	 * 
	 * @param thumbnailIndex
	 *            The thumbnail index to check.
	 * @throws IndexOutOfBoundsException
	 *             If the thumbnail does not exist.
	 */
	private void checkThumbnailIndex(final int thumbnailIndex) {
		if (thumbnailIndex < 0 || (streamMeta.hasMipmaps() ? streamMeta.getMipmapCount() - 1 : 0) <= thumbnailIndex) {
			throw new IndexOutOfBoundsException(thumbnailIndex);
		}
	}

	@Override
	public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
		// Parent performs type checks and generates exceptions.
		super.setInput(input, seekForwardOnly, ignoreMetadata);

		// close internal ImageInputStream
		if (intSrc != null) {
			try {
				intSrc.close();
			} catch (IOException e) {
				processWarningOccurred(
						new LocalizedFormatedString("com.hiveworkshop.text.blp", "ISCloseFail", e.getMessage()));
			}
			intSrc = null;
		}

		streamMeta = null;
		mipmapReader = null;
	}

	/**
	 * Sends all attached warning listeners a warning message. The messages will be
	 * localized for each warning listener.
	 * 
	 * @param msg
	 *            the warning message to send to all warning listeners.
	 */
	protected void processWarningOccurred(LocalizedFormatedString msg) {
		if (warningListeners == null)
			return;
		else if (msg == null)
			throw new IllegalArgumentException("msg is null.");
		int numListeners = warningListeners.size();
		for (int i = 0; i < numListeners; i++) {
			IIOReadWarningListener listener = warningListeners.get(i);
			Locale locale = (Locale) warningLocales.get(i);
			if (locale == null) {
				locale = Locale.getDefault();
			}
			listener.warningOccurred(this, msg.toString(locale));
		}
	}

	@Override
	public int getHeight(int imageIndex) throws IOException {
		loadHeader();
		checkImageIndex(imageIndex);
		return streamMeta.getHeight(FULL_IMAGE_MIPMAP_INDEX);
	}

	@Override
	public IIOMetadata getImageMetadata(int imageIndex) throws IOException {
		checkImageIndex(imageIndex);
		return null;
	}

	@Override
	public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
		loadHeader();
		checkImageIndex(imageIndex);

		return mipmapProcessor.getSupportedImageTypes(1, 1);
	}

	@Override
	public int getNumImages(final boolean allowSearch) throws IOException {
		loadHeader();
		return 1;
	}

	@Override
	public int getNumThumbnails(final int imageIndex) throws IOException {
		loadHeader();
		checkImageIndex(imageIndex);
		return streamMeta.hasMipmaps() ? streamMeta.getMipmapCount() - 1 : 0;
	}

	@Override
	public boolean hasThumbnails(final int imageIndex) throws IOException {
		loadHeader();
		checkImageIndex(imageIndex);
		return streamMeta.hasMipmaps();
	}

	@Override
	public IIOMetadata getStreamMetadata() throws IOException {
		loadHeader();
		return streamMeta;
	}

	@Override
	public int getWidth(int imageIndex) throws IOException {
		loadHeader();
		checkImageIndex(imageIndex);
		return streamMeta.getWidth(FULL_IMAGE_MIPMAP_INDEX);
	}

	/**
	 * Read a mipmap image from the BLP file.
	 * 
	 * @param mipmapIndex
	 *            Mipmap index of image to be read.
	 * @param param
	 *            ImageReadParam to use.
	 * @return Mipmap image.
	 * @throws IOException
	 *             If IOException occurs while reading.
	 */
	private BufferedImage readMipmap(final int mipmapIndex, ImageReadParam param) throws IOException {
		if (!mipmapProcessor.canDecode()) {
			throw new IIOException("Mipmap processor cannot decode.");
		}

		// Get mipmap image data.
		byte[] mmData = mipmapReader.getMipmapDataChunk(mipmapIndex);

		// Unpack mipmap image data into a mipmap image.
		final int width = streamMeta.getWidth(mipmapIndex);
		final int height = streamMeta.getHeight(mipmapIndex);
		BufferedImage srcImg = mipmapProcessor.decodeMipmap(mmData, param, width, height, this::processWarningOccurred);

		BufferedImage destImg;
		if (direct) {
			// Direct read bypasses.
			destImg = srcImg;
		} else {
			// Use image read param.
			if (param == null) {
				param = getDefaultReadParam();
			}
			destImg = getDestination(param, mipmapProcessor.getSupportedImageTypes(1, 1), srcImg.getWidth(),
					srcImg.getHeight());

			checkReadParamBandSettings(param, srcImg.getSampleModel().getNumBands(),
					destImg.getSampleModel().getNumBands());

			Rectangle srcRegion = new Rectangle();
			Rectangle destRegion = new Rectangle();
			computeRegions(param, srcImg.getWidth(), srcImg.getHeight(), destImg, srcRegion, destRegion);

			// Extract param settings.
			int[] srcBands = param.getSourceBands();
			int[] destBands = param.getDestinationBands();
			int ssX = param.getSourceXSubsampling();
			int ssY = param.getSourceYSubsampling();

			WritableRaster srcRaster = srcImg.getRaster().createWritableChild(srcRegion.x, srcRegion.y, srcRegion.width,
					srcRegion.height, 0, 0, srcBands);
			WritableRaster destRaster = destImg.getRaster().createWritableChild(destRegion.x, destRegion.y,
					destRegion.width, destRegion.height, 0, 0, destBands);

			// Copy pixels.
			Object dataElements = null;
			for (int y = 0; y < destRegion.height; y += 1) {
				for (int x = 0; x < destRegion.width; x += 1) {
					final int srcXOff = ssX * x;
					final int srcYOff = ssY * y;
					dataElements = srcRaster.getDataElements(srcXOff, srcYOff, null);
					destRaster.setDataElements(x, y, dataElements);
				}
			}
		}

		return destImg;
	}

	@Override
	public BufferedImage read(final int imageIndex, ImageReadParam param) throws IOException {
		loadHeader();
		checkImageIndex(imageIndex);

		// Seek forward functionality.
		if (seekForwardOnly) {
			mipmapReader.flushTo(FULL_IMAGE_MIPMAP_INDEX);
		}

		processImageStarted(imageIndex);

		direct = param instanceof BLPReadParam && ((BLPReadParam) param).isDirectRead();

		final BufferedImage image = readMipmap(FULL_IMAGE_MIPMAP_INDEX, param);

		processImageComplete();
		return image;
	}

	public static int convertThumbnailIndexToMipmapIndex(final int thumbnailIndex) {
		return 1 + thumbnailIndex;
	}

	@Override
	public int getThumbnailHeight(final int imageIndex, final int thumbnailIndex) throws IOException {
		loadHeader();
		checkImageIndex(imageIndex);
		checkThumbnailIndex(thumbnailIndex);
		return streamMeta.getHeight(convertThumbnailIndexToMipmapIndex(thumbnailIndex));
	}

	@Override
	public int getThumbnailWidth(final int imageIndex, final int thumbnailIndex) throws IOException {
		loadHeader();
		checkImageIndex(imageIndex);
		checkThumbnailIndex(thumbnailIndex);
		return streamMeta.getWidth(convertThumbnailIndexToMipmapIndex(thumbnailIndex));
	}

	@Override
	public BufferedImage readThumbnail(final int imageIndex, final int thumbnailIndex) throws IOException {
		loadHeader();
		checkImageIndex(imageIndex);
		processThumbnailStarted(imageIndex, thumbnailIndex);

		final BufferedImage image = readMipmap(convertThumbnailIndexToMipmapIndex(thumbnailIndex), null);

		processThumbnailComplete();
		return image;
	}

	@Override
	public void dispose() {
		setInput(null);
	}

	@Override
	public ImageReadParam getDefaultReadParam() {
		return new BLPReadParam();
	}
}
