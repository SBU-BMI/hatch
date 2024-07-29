package edu.stonybrook.bmi.hatch;

import java.io.IOException;
import java.util.ArrayList;
import static loci.formats.FormatHandler.checkSuffix;
import loci.formats.FormatTools;
import loci.formats.dicom.DicomJSONProvider;
import loci.formats.dicom.ITagProvider;
import loci.formats.out.DicomWriter;

/**
 *
 * @author erich
 */
public class ExtendedDicomWriter extends DicomWriter {
    private final ArrayList<ITagProvider> tagProviders = new ArrayList<>();

    @Override
    public void setExtraMetadata(String tagSource) {
        FormatTools.assertId(currentId, false, 1);

        // get the provider (parser) from the source name
        // uses the file extension, this might need improvement

        if (tagSource != null) {
            ITagProvider provider = null;
            if (checkSuffix(tagSource, "json")) {
                provider = new DicomJSONProvider();
            } else {
                throw new IllegalArgumentException("Unknown tag format: " + tagSource);
            }
            try {
                provider.readTagSource(tagSource);
                tagProviders.add(provider);
            } catch (IOException e) {
                LOGGER.error("Could not parse extra metadata: " + tagSource, e);
            }
        }
  }
 
    public static void main(String args[]) {
        // TODO code application logic here
        ExtendedDicomWriter dw = new ExtendedDicomWriter();
        
    }
}
