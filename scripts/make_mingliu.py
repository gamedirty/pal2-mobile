from fontTools.ttLib import TTFont

def make_variant(src_path, out_path, family_zh):
    f = TTFont(src_path, fontNumber=0)
    table = f['name']
    table.names = []
    # Family name in every slot, Chinese in the Windows English position too —
    # Wine indexes GDI families by the primary name, and the game asks for the
    # Traditional Chinese face name directly.
    table.setName(family_zh, 1, 3, 1, 0x409)
    table.setName(family_zh, 1, 3, 1, 0x404)
    table.setName(family_zh, 1, 3, 1, 0x804)
    table.setName('Regular', 2, 3, 1, 0x409)
    table.setName('Regular', 2, 3, 1, 0x404)
    table.setName(family_zh + ' Regular', 3, 3, 1, 0x409)
    table.setName(family_zh, 4, 3, 1, 0x409)
    table.setName(family_zh, 4, 3, 1, 0x404)
    table.setName(family_zh.replace('\u7d30', '').replace('\u9ad4', '') or 'MingLiu', 6, 3, 1, 0x409)
    os2 = f['OS/2']
    cp = os2.ulCodePageRange1 or 0
    cp |= (1 << 3) | (1 << 16) | (1 << 17) | (1 << 18) | (1 << 19) | (1 << 20)
    os2.ulCodePageRange1 = cp
    f.save(out_path)
    print('saved', out_path)

base = r'C:\Windows\Fonts\simsun.ttc'
make_variant(base, r'F:\palmobile\pal2-android\app\src\main\assets\pal2-fonts\pmingliu-pal2.ttf', '\u65b0\u7d30\u660e\u9ad4')
make_variant(base, r'F:\palmobile\pal2-android\app\src\main\assets\pal2-fonts\mingliu-pal2.ttf', '\u7d30\u660e\u9ad4')
