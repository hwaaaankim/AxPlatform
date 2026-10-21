export function el(tag,text='',className='') {
  const node=document.createElement(tag);
  node.textContent=text;
  if(className)node.className=className;
  return node;
}
export function button(label,handler,className='button small') {
  const b=el('button',label,className);
  b.type='button';
  b.addEventListener('click',handler);
  return b;
}
export function message(node,text,error=false) {
  node.textContent=text;
  node.classList.toggle('error',error);
}
export async function busy(node,action) {
  const controls=[...node.querySelectorAll('button,input,select,textarea')];
  const states=controls.map(x=>x.disabled);
  controls.forEach(x=>x.disabled=true);
  try {
    return await action();
  }
  finally {
    controls.forEach((x,i)=>x.disabled=states[i]);
  }
}
export function whenText(value) {
  if(!value)return '없음';
  const normalized=typeof value==='string'&&/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)&&!/[zZ]|[+-]\d{2}:?\d{2}$/.test(value)?value+'Z':value; const d=new Date(normalized);
  return Number.isNaN(d.getTime())?String(value):d.toLocaleString('ko-KR');
}
export function bool(value) {
  return value===true||value===1;
}
