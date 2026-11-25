"""blender -b -P hdr_convert.py -- <in.exr> <out.hdr> <width>  : downsample an EXR panorama to Radiance .hdr"""
import bpy, sys
a = sys.argv[sys.argv.index('--') + 1:]
im = bpy.data.images.load(a[0])
w = int(a[2]); im.scale(w, w // 2)
im.file_format = 'HDR'
im.filepath_raw = a[1]
im.save()
print('saved', a[1])
