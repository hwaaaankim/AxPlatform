export const TIERS=['GUEST','TIER1','TIER2','TIER3'];
export const available=a=>a.status==='ACTIVE'&&a.group_status==='ACTIVE'&&a.implemented===true&&a.admin_only!==true&&a.admin_only!==1;
export function dependencyAdd(catalog,grants,codes) {
  const index=new Map(catalog.map(a=>[a.code,a])),wanted=new Set(),visiting=new Set();
  function visit(code) {
    const a=index.get(code);
    if(!a||!available(a))throw new Error('배정할 수 없는 기능 또는 의존 기능: '+code);
    if(visiting.has(code))throw new Error('의존 기능에 순환이 있습니다.');
    if(wanted.has(code))return;
    visiting.add(code);
    for(const dep of a.dependencies||[])visit(dep);
    visiting.delete(code);
    wanted.add(code);
  }
  for(const code of codes)visit(code);
  const added=[...wanted].filter(x=>!grants.includes(x));
  return {
    added,dependencies:added.filter(x=>!codes.includes(x)),next:[...grants,...added]
  }
  ;
}
export function dependencyRemove(catalog,grants,code) {
  const index=new Map(catalog.map(a=>[a.code,a])),removed=new Set([code]);
  let changed=true;
  while(changed) {
    changed=false;
    for(const key of grants) {
      if(!removed.has(key)&&(index.get(key)?.dependencies||[]).some(d=>removed.has(d))) {
        removed.add(key);
        changed=true;
      }
    }
  }
  return {
    removed:[...removed].filter(x=>grants.includes(x)),next:grants.filter(x=>!removed.has(x))
  }
  ;
}
