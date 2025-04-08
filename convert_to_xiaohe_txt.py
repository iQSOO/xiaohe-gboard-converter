from pypinyin import pinyin, Style

xiaohe_map = {
    'a': 'a','ai': 'd','an': 'j','ang': 'h','ao': 'c',
    'e': 'e','ei': 'w','en': 'f','eng': 'g','er': 'r',
    'o': 'o','ou': 'z','ong': 's',
    'i': 'i','ia': 'x','ian': 'm','iang': 'l','iao': 'n','ie': 'p',
    'in': 'b','ing': 'k','iong': 's','iu': 'q',
    'u': 'u','ua': 'x','uai': 'k','uan': 'r','uang': 'l','ue': 't',
    'ui': 'v','un': 'y',
    'ü': 'v','üan': 't','ün': 'y'
}

initial_map = {
    'b': 'b','p': 'p','m': 'm','f': 'f','d': 'd','t': 't','n': 'n','l': 'l',
    'g': 'g','k': 'k','h': 'h','j': 'j','q': 'q','x': 'x','zh': 'v','ch': 'i','sh': 'u',
    'r': 'r','z': 'z','c': 'c','s': 's','y': 'y','w': 'w','': ''
}

def convert_to_xiaohe(word):
    result = ''
    initials = pinyin(word, style=Style.INITIALS, strict=False)
    finals = pinyin(word, style=Style.FINALS_TONE3, strict=False)
    for i in range(len(word)):
        sm = initials[i][0] or ''
        ym = finals[i][0].rstrip('12345')
        result += initial_map.get(sm, '') + xiaohe_map.get(ym, '')
    return result

with open('dictionary.txt', 'r', encoding='utf-8') as infile, open('gboard_xiaohe.txt', 'w', encoding='utf-8') as outfile:
    for line in infile:
        if line.startswith('#') or not line.strip():
            continue
        parts = line.strip().split('\t')
        if len(parts) >= 2:
            _, word = parts[:2]
            code = convert_to_xiaohe(word)
            outfile.write(f"{code} {word}\n")

print("完成转换，输出文件为 gboard_xiaohe.txt")