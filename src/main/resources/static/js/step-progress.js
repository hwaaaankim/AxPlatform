// Prerequisites follow the actual work: basics, source, editing, review, saved view.
export const WORK_STEPS=['start','basics','prepare','edit','review','finish'];
export function createStepProgress(existing=false){
 const required=['basics','prepare','edit'],complete=new Set(existing?required:[]);
 return {complete:key=>complete.add(key),reset:key=>complete.delete(key),has:key=>complete.has(key),
  missing(next){const at=WORK_STEPS.indexOf(next);return required.find(key=>WORK_STEPS.indexOf(key)<at&&!complete.has(key));},
  paint(root,current){root.querySelectorAll('[data-stage]').forEach(b=>{b.setAttribute('aria-current',b.dataset.stage===current?'step':'false');b.dataset.complete=String(complete.has(b.dataset.stage));b.setAttribute('aria-disabled',String(!!this.missing(b.dataset.stage)));});}
 };
}
