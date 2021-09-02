# Hatch

This tool converts VSI image format to TIFF.

Features:
1) Hatch transfers raw jpeg encodings from original image to new image to prevent any re-encoding image loss due to re-compression.
   OME Bioformats always decodes and then re-encodes when using their tools like bfconvert.

2) Only transfers max size from original source files and drops all other images including cover slide images

3) image pyramid is re-created from original image

Usage:
```
hatch <src> <dest>
```

Credits and Acknowledgements:
Many of the core files are modified files from the OME Bioformats Projects (https://github.com/ome/bioformats) such as (but not limited to):
CellSensReader
OMETiffWriter
TiffCompression
TiffParser
TiffSaver
TiffWriter

