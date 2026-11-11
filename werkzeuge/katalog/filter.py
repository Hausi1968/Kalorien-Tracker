import sys, csv, json
csv.field_size_limit(10**9)
r = csv.reader(sys.stdin, delimiter='\t', quoting=csv.QUOTE_NONE)
kopf = next(r); i = {n: k for k, n in enumerate(kopf)}
DACH = ('en:germany', 'en:austria', 'en:switzerland')
def zahl(s):
    try:
        v = float(s); return v if v == v else None
    except: return None
out = open(sys.argv[1], 'w', encoding='utf-8'); n = 0; alle = 0
for z in r:
    alle += 1
    if len(z) < len(kopf): continue
    if not any(d in z[i['countries_tags']] for d in DACH): continue
    name = z[i['product_name']].strip()
    kcal = zahl(z[i['energy-kcal_100g']])
    if kcal is None:
        kj = zahl(z[i['energy_100g']]); kcal = kj / 4.184 if kj else None
    if not name or kcal is None or kcal < 0 or kcal > 950: continue
    code = z[i['code']].strip()
    if not code.isdigit() or len(code) not in (8, 12, 13, 14): continue
    d = dict(code=code, name=name, brand=z[i['brands']].split(',')[0].strip() or None, q=z[i['quantity']].strip() or None,
             sg=zahl(z[i['serving_quantity']]), kcal=round(kcal, 1), scans=int(zahl(z[i['unique_scans_n']]) or 0),
             c=countries if (countries:=z[i['countries_tags']]) else '')
    for k, s in (('fett','fat_100g'),('gfett','saturated-fat_100g'),('kh','carbohydrates_100g'),('zucker','sugars_100g'),('bst','fiber_100g'),('eiw','proteins_100g'),('salz','salt_100g')):
        v = zahl(z[i[s]]); d[k] = round(v, 2) if v is not None and 0 <= v <= 100 else None
    out.write(json.dumps(d, ensure_ascii=False) + '\n'); n += 1
    if n % 50000 == 0: print(n, alle, file=sys.stderr, flush=True)
print('fertig', n, alle, file=sys.stderr)
