import tarfile, os
import zstandard as zstd

SRC = r'C:\pal2'
OUT = r'F:\palmobile\pal2-android\app\src\main\assets\pal2-game.tzst'
EXCLUDE_FILES = {'UNWISE.EXE', 'unwise.exe', 'Uninstall.exe', 'First.exe', 'SysInfo.exe', 'INSTALL.LOG', 'install.log'}
EXCLUDE_DIRS = {'uninstall'}

cctx = zstd.ZstdCompressor(level=19)
count, total = 0, 0
with open(OUT, 'wb') as fout:
    with cctx.stream_writer(fout, closefd=False) as zwriter:
        with tarfile.open(fileobj=zwriter, mode='w|', format=tarfile.USTAR_FORMAT) as tf:
            for root, dirs, files in os.walk(SRC):
                rel = os.path.relpath(root, SRC)
                dirs[:] = sorted(d for d in dirs if d not in EXCLUDE_DIRS)

                # directory entry first (parents precede children in os.walk)
                arc_dir = '.' if rel == '.' else './' + rel.replace(os.sep, '/')
                di = tf.gettarinfo(root, arcname=arc_dir)
                di.uid = di.gid = 0
                di.uname = di.gname = ''
                di.mode = 0o771
                tf.addfile(di)
                count += 1

                for f in sorted(files):
                    if f in EXCLUDE_FILES: continue
                    full = os.path.join(root, f)
                    arc = arc_dir + '/' + f
                    ti = tf.gettarinfo(full, arcname=arc)
                    ti.uid = ti.gid = 0
                    ti.uname = ti.gname = ''
                    ti.mode = 0o771 if ti.isfile() else ti.mode
                    if ti.isfile():
                        with open(full, 'rb') as fh:
                            tf.addfile(ti, fh)
                            total += ti.size
                    else:
                        tf.addfile(ti)
                    count += 1
print('entries:', count, 'raw bytes:', total, '->', os.path.getsize(OUT))
