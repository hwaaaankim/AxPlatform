// Shared workflow progression: completed stages remain editable, later stages require their input.
export const WORK_STEPS=['start','prepare','basics','review','edit','finish'];
export function createStepProgress(existing=false){
 const complete=new Set(existing?['prepare','basics','review']:[]);
 return {complete:key=>complete.add(key),reset:key=>complete.delete(key),has:key=>complete.has(key),
  missing(next){const at=WORK_STEPS.indexOf(next);return ['prepare','basics','review'].find(key=>WORK_STEPS.indexOf(key)<at&&!complete.has(key));},
  paint(root,current){root.querySelectorAll('[data-stage]').forEach(b=>{b.setAttribute('aria-current',b.dataset.stage===current?'step':'false');b.dataset.complete=String(complete.has(b.dataset.stage));b.setAttribute('aria-disabled',String(!!this.missing(b.dataset.stage)));});}
 };
}
