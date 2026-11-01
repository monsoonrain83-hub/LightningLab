import subprocess,json,re,sys,glob,collections
jar=sys.argv[1]; xargs=sys.argv[2].split(','); senses=[float(x) for x in sys.argv[3:]]
def run(f,s):
    o=subprocess.run(['java','-cp',jar,'HarnessKt',f,str(s)]+[],capture_output=True,text=True).stdout
    return [int(x) for x in re.findall(r'(\d+)ms\(',o)]
for s in senses:
    hit=collections.Counter(); tot=collections.Counter(); fp=0; extra=0; nclean=0
    for nm in ('stable','noisy','shaky'):
        for seed in (1,2):
            tag=f'{nm}{seed}'; truth=json.load(open(f'{tag}_truth.json'))
            ev=run(f'{tag}_bolts.bin',s); cl=run(f'{tag}_clean.bin',s)
            fp+=len(cl); nclean+=1
            used=set()
            for t,a in truth:
                tot[a]+=1
                m=[e for e in ev if abs(e-t)<=170]
                if m: hit[a]+=1; used.update(m)
            extra+=len([e for e in ev if e not in used])
    print(f'{xargs} sens={s}: recall by amp',{a:f'{hit[a]}/{tot[a]}' for a in sorted(tot)},'| false events on CLEAN clips total',fp,'in',nclean,'clips | extra events in bolt clips',extra)
