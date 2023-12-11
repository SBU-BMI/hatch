# Hatch 3.1.2


This tool converts the largest image in a VSI, SVS, or TIF image into a new TIFF image with a freshly created image pyramid with each scaling 1/2 dimensions each scale.

Features:
1) Hatch transfers raw jpeg encodings from original image to new image to prevent any re-encoding image loss due to re-compression.
   OME Bioformats always decodes and then re-encodes when using their tools like bfconvert.

2) Only transfers max size from original source files and drops all other images including cover slide images

3) image pyramid is re-created from original image

Build with:

Jar version
```
mvn -Phatchjar clean package
```
Native-image version (requires fully configured Graalvm native-image environment)
```
mvn -Phatch clean package
```

Usage:
```
hatch -src <src> -dest <dest>

or see

hatch -help
```

Credits and Acknowledgements:
Many of the core files are modified files from the OME Bioformats Projects (https://github.com/ome/bioformats) such as (but not limited to):
CellSensReader
TiffCompression
TiffParser

