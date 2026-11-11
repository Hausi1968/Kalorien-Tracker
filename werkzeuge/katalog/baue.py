"""Baut assets/katalog.tsv.gz aus dach.jsonl: Dubletten raus, nur plausible Nährwerte, die beliebtesten N."""
import sys, json, re, gzip
quelle, ziel, anzahl = sys.argv[1], sys.argv[2], int(sys.argv[3])
def menge(q):
    """'500 g' -> (500,'g'); '1,5 l' -> (1500,'ml'); '6 x 0,33 l' -> (1980,'ml')"""
    if not q: return None, None
    s = q.lower().replace(',', '.')
    m = re.search(r'(?:(\d+)\s*[x×]\s*)?(\d+(?:\.\d+)?)\s*(kg|g|gr|mg|ml|cl|dl|l)\b', s)
    if not m: return None, None
    n = float(m.group(1) or 1) * float(m.group(2)); e = m.group(3)
    f = {'kg': (1000, 'g'), 'g': (1, 'g'), 'gr': (1, 'g'), 'mg': (0.001, 'g'), 'ml': (1, 'ml'), 'cl': (10, 'ml'), 'dl': (100, 'ml'), 'l': (1000, 'ml')}[e]
    return n * f[0], f[1]
def plausibel(d):
    if None in (d['fett'], d['kh'], d['eiw']): return False
    if d['zucker'] is not None and d['zucker'] > d['kh'] + 0.5: return False
    if d['gfett'] is not None and d['gfett'] > d['fett'] + 0.5: return False
    if d['fett'] + d['kh'] + d['eiw'] > 101: return False
    ber = 9 * d['fett'] + 4 * d['kh'] + 4 * d['eiw'] + 2 * (d['bst'] or 0)
    return abs(ber - d['kcal']) <= max(25, 0.2 * d['kcal']) or d['kcal'] < 5 and ber < 30
FREMD = re.compile(r"[éèêàçôûîïëœøåñòìùáíóú]|[\u0400-\u04FF\u0370-\u03FF]|\b(de|du|des|au|aux|et|le|la|les|avec|sans|pour|di|della|con|y|el|van|het|met|chocolat|lait|fromage|biscuits?|saucisses?|petit|déjeuner|jambon|poulet|pâtes|sucre|beurre|noix)\b", re.I)
def deutsch_genug(name):
    return not FREMD.search(name)
beste = {}
for z in open(quelle, encoding='utf-8'):
    d = json.loads(z)
    if not plausibel(d): continue
    # Deutschsprachige Produkte: EAN aus D/A/CH (40-44, 90-91, 76) oder Handelsmarken (2…), sonst nur
    # Produkte, die hauptsächlich in D/A/CH verkauft werden (fremdsprachige Namen sollen nicht dominieren)
    laender = [c for c in d['c'].split(',') if c]
    dach = d['code'][:2] in ('40','41','42','43','44','90','91','76') or d['code'][:1] == '2'
    if not dach and (len(laender) > 3 or 'en:france' in laender or 'en:italy' in laender or 'en:spain' in laender): continue
    name = re.sub(r'\s+', ' ', d['name']).strip()[:120]
    if len(name) < 2 or '\t' in name: continue
    d['name'] = name
    if not deutsch_genug(name): continue
    alt = beste.get(d['code'])
    if alt is None or d['scans'] > alt['scans']: beste[d['code']] = d
liste = sorted(beste.values(), key=lambda d: -d['scans'])[:anzahl]
def t(v): return '' if v is None else ('%g' % v)
with gzip.open(ziel, 'wt', encoding='utf-8', compresslevel=9) as out:
    out.write('ean\tname\tmarke\teinheit\tkcal\tfett\tgesfett\tkh\tzucker\tballaststoffe\teiweiss\tsalz\tportion\tpackung\tbeliebtheit\n')
    for d in liste:
        pack, einheit = menge(d['q'])
        einheit = einheit or 'g'
        portion = d['sg'] if d['sg'] and 1 <= d['sg'] <= 2000 else None
        if pack is not None and not (1 <= pack <= 10000): pack = None
        marke = (d['brand'] or '').replace('\t', ' ')[:60]
        out.write('\t'.join([d['code'], d['name'], marke, einheit, t(d['kcal']), t(d['fett']), t(d['gfett']), t(d['kh']), t(d['zucker']),
                             t(d['bst']), t(d['eiw']), t(d['salz']), t(portion and round(portion, 1)), t(pack and round(pack, 1)), str(d['scans'])]) + '\n')
print('Produkte', len(liste), 'von', len(beste), 'plausiblen; mit Scans >0:', sum(1 for d in liste if d['scans'] > 0))
