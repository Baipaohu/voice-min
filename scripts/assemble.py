import os
import zipfile

here = os.path.dirname(os.path.abspath(__file__))
root = os.path.dirname(here)
build = os.path.join(root, 'build')
base = os.path.join(build, 'base.apk')
out = os.path.join(build, 'assembled.apk')
dex = os.path.join(build, 'dex', 'classes.dex')
assets = os.path.join(root, 'assets')
jni = os.path.join(root, 'libs', 'jni')


def addf(zf, src, arc, compress):
    zf.write(src, arc, compress_type=compress)


with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zo:
    with zipfile.ZipFile(base, 'r') as zin:
        for item in zin.infolist():
            data = zin.read(item.filename)
            zi = zipfile.ZipInfo(item.filename)
            zi.compress_type = item.compress_type
            zo.writestr(zi, data)

    # 模型资源：必须 STORED（不压缩），AAsset_getBuffer 才能工作
    for fn in sorted(os.listdir(assets)):
        p = os.path.join(assets, fn)
        if os.path.isfile(p):
            addf(zo, p, 'assets/' + fn, zipfile.ZIP_STORED)

    # 原生库：STORED
    for root_dir, dirs, files in os.walk(jni):
        for f in files:
            p = os.path.join(root_dir, f)
            rel = os.path.relpath(p, jni).replace('\\', '/')
            addf(zo, p, 'lib/' + rel, zipfile.ZIP_STORED)

    addf(zo, dex, 'classes.dex', zipfile.ZIP_DEFLATED)

print('assembled bytes', os.path.getsize(out))
