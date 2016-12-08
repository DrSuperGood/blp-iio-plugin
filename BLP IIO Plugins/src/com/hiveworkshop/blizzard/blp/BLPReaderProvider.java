package com.hiveworkshop.blizzard.blp;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Locale;

import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import com.hiveworkshop.lang.MagicInt;

/**
 * Service provider for reading BLP image files.
 * 
 * @author ImperialGood
 *
 */
public class BLPReaderProvider extends ImageReaderSpi {

	// format specification should be moved to a common class when a writer is
	// implemented
	// file format specification
	private static final String vendorName = "Imperial Good";
	private static final String version = "1.0";
	private static final String[] names = { "Blizzard Picture", "blp" };
	private static final String[] suffixes = { "blp" };
	private static final String[] MIMETypes = { "image/prs.blp" };
	private static final String readerClassName = "com.hiveworkshop.blizzard.blp.BLPReader";
	private static final Class<?>[] inputTypes = { ImageInputStream.class, File.class, Path.class };
	private static final String[] writerSpiNames = null;

	// metadata format specification
	private static final boolean supportsStandardStreamMetadataFormat = false;
	private static final String nativeStreamMetadataFormatName = null;
	private static final String nativeStreamMetadataFormatClassName = null;
	private static final String[] extraStreamMetadataFormatNames = null;
	private static final String[] extraStreamMetadataFormatClassNames = null;
	private static final boolean supportsStandardImageMetadataFormat = false;
	private static final String nativeImageMetadataFormatName = null;
	private static final String nativeImageMetadataFormatClassName = null;
	private static final String[] extraImageMetadataFormatNames = null;
	private static final String[] extraImageMetadataFormatClassNames = null;

	public BLPReaderProvider() {
		super(vendorName, version, names, suffixes, MIMETypes, readerClassName,
				inputTypes, writerSpiNames,
				supportsStandardStreamMetadataFormat,
				nativeStreamMetadataFormatName,
				nativeStreamMetadataFormatClassName,
				extraStreamMetadataFormatNames,
				extraStreamMetadataFormatClassNames,
				supportsStandardImageMetadataFormat,
				nativeImageMetadataFormatName,
				nativeImageMetadataFormatClassName,
				extraImageMetadataFormatNames,
				extraImageMetadataFormatClassNames);
	}

	@Override
	public boolean canDecodeInput(Object source) throws IOException {
		// case of ImageInputStream
		if (source instanceof ImageInputStream) {
			// prepare stream
			ImageInputStream src = (ImageInputStream) source;
			src.mark();

			// extract magic number
			src.setByteOrder(ByteOrder.LITTLE_ENDIAN);
			MagicInt magic = new MagicInt(src.readInt(), ByteOrder.LITTLE_ENDIAN);
			
			// rewind stream
			src.reset();

			// validate magic number
			if ( BLPCommon.resolveVersion(magic) != -1 ) return true;
		}

		return false;
	}

	@Override
	public ImageReader createReaderInstance(Object arg0) throws IOException {
		return new BLPReader(this);
	}

	@Override
	public String getDescription(Locale locale) {
		return "Blizzard Image file image reader.";
	}

}
