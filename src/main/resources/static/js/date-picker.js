import {el,button} from './ui.js?v=0.9.1';
const instances=new WeakMap();
const pad=n=>String(n).padStart(2,'0');
function dayValue(year,month,day){return String(year).padStart(4,'0')+'-'+pad(month)+'-'+pad(day);}
function utcDay(year,month,day){const d=new Date(0);d.setUTCHours(0,0,0,0);d.setUTCFullYear(year,month-1,day);return d;}
export function validDateValue(value,kind='date'){
  const match=String(value).match(kind==='datetime-local'?/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/:/^(\d{4})-(\d{2})-(\d{2})$/);
  if(!match)return false;
  const [year,month,day]=match.slice(1,4).map(Number),date=utcDay(year,month,day);
  return year>=1&&month>=1&&month<=12&&date.getUTCFullYear()===year&&date.getUTCMonth()===month-1&&date.getUTCDate()===day&&(kind!=='datetime-local'||(+match[4]<24&&+match[5]<60&&(!match[6]||+match[6]<60)));
}
export function enhanceDateInput(input){
  if(instances.has(input))return instances.get(input);
  const kind=input.dataset.dateKind||input.type;
  if(!['date','datetime-local'].includes(kind))return null;
  // Keep the original input, id, form name and ISO value. Invalid imported text
  // remains visible rather than being silently erased by a native date input.
  const value=input.value,wrap=el('div','','date-control');
  input.dataset.dateKind=kind;input.type='text';input.value=value;input.autocomplete='off';
  input.placeholder=kind==='date'?'YYYY-MM-DD':'YYYY-MM-DDTHH:mm';
  input.before(wrap);wrap.append(input);
  const open=button('',()=>show(),'date-open');open.setAttribute('aria-label',(input.getAttribute('aria-label')||'날짜')+' 달력 열기');open.title='달력에서 선택';
  const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('aria-hidden','true');
  const path=document.createElementNS(svg.namespaceURI,'path');path.setAttribute('d','M4 5h16v16H4zM8 2v6M16 2v6M4 10h16M8 14h2M14 14h2M8 17h2');svg.append(path);open.append(svg);wrap.append(open);
  let dialog=null;
  const blocked=()=>input.disabled||input.readOnly||!!input.closest('fieldset:disabled');
  function validate(){
    const v=input.value;let error='';
    if(v&&!validDateValue(v,kind))error=kind==='date'?'실제 존재하는 날짜를 YYYY-MM-DD 형식으로 입력하세요.':'날짜·시간을 YYYY-MM-DDTHH:mm 형식으로 입력하세요.';
    else if(v&&input.min&&v<input.min)error=input.min+' 이후를 선택하세요.';
    else if(v&&input.max&&v>input.max)error=input.max+' 이전을 선택하세요.';
    input.setCustomValidity(error);wrap.classList.toggle('has-error',!!error);if(error)input.setAttribute('aria-invalid','true');else input.removeAttribute('aria-invalid');
    open.disabled=blocked();return !error;
  }
  function show(){
    if(blocked()||dialog||document.querySelector('dialog[open]'))return;
    const now=new Date(),today=dayValue(now.getFullYear(),now.getMonth()+1,now.getDate());
    let chosen=validDateValue(input.value,kind)?input.value.slice(0,10):today;
    let year=Number(chosen.slice(0,4)),month=Number(chosen.slice(5,7));
    dialog=el('dialog','','date-dialog');dialog.setAttribute('aria-label',kind==='date'?'날짜 선택':'날짜와 시간 선택');
    const top=el('div','','date-dialog-heading'),label=el('h2',kind==='date'?'날짜 선택':'날짜·시간 선택');
    const close=()=>{if(!dialog)return;dialog.close();dialog.remove();dialog=null;if(input.isConnected)input.focus({preventScroll:true});};
    const cancelIcon=button('×',close,'file-remove');cancelIcon.setAttribute('aria-label','날짜 선택 취소');top.append(label,cancelIcon);
    const navigation=el('div','','calendar-navigation'),yearInput=el('input'),monthInput=el('select');
    yearInput.type='number';yearInput.min='1';yearInput.max='9999';yearInput.setAttribute('aria-label','연도');
    monthInput.setAttribute('aria-label','월');for(let m=1;m<=12;m++){const o=el('option',m+'월');o.value=m;monthInput.append(o);}
    const previous=button('‹',()=>moveMonth(-1),'calendar-nav'),next=button('›',()=>moveMonth(1),'calendar-nav');
    previous.setAttribute('aria-label','이전 달');next.setAttribute('aria-label','다음 달');navigation.append(previous,yearInput,monthInput,next);
    const grid=el('div','','calendar-grid');grid.setAttribute('role','grid');grid.setAttribute('aria-label','날짜');
    const selected=el('p','','date-selection'),warning=el('p','','help-line error');selected.setAttribute('role','status');warning.setAttribute('role','status');
    const time=el('div','','calendar-time'),hour=el('select'),minute=el('select');hour.setAttribute('aria-label','시');minute.setAttribute('aria-label','분');
    for(let h=0;h<24;h++){const o=el('option',pad(h)+'시');o.value=pad(h);hour.append(o);}
    for(let m=0;m<60;m++){const o=el('option',pad(m)+'분');o.value=pad(m);minute.append(o);}
    hour.value=validDateValue(input.value,kind)&&kind==='datetime-local'?input.value.slice(11,13):pad(now.getHours());
    minute.value=validDateValue(input.value,kind)&&kind==='datetime-local'?input.value.slice(14,16):pad(now.getMinutes());
    time.append(el('span','시간'),hour,minute);time.hidden=kind==='date';
    const value=()=>chosen+(kind==='datetime-local'?'T'+hour.value+':'+minute.value:'');
    const allowed=date=>(!input.min||date>=input.min.slice(0,10))&&(!input.max||date<=input.max.slice(0,10));
    function commit(v){
      if(blocked())return close();
      if(v&&(!validDateValue(v,kind)||(input.min&&v<input.min)||(input.max&&v>input.max))){warning.textContent='입력 가능한 날짜·시간 범위를 확인하세요.';return;}
      if(!v&&input.required){warning.textContent='필수 날짜는 비울 수 없습니다.';return;}
      input.value=v;validate();input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));close();
    }
    const actions=el('div','','actions'),todayButton=button('오늘',()=>{chosen=today;year=now.getFullYear();month=now.getMonth()+1;render();},'button small');
    todayButton.disabled=!allowed(today);const clear=button('비우기',()=>commit(''),'button small');clear.disabled=input.required;
    actions.append(todayButton,clear,button('취소',close,'button small'),button('적용',()=>commit(value()),'button primary small'));
    function moveMonth(delta){const d=utcDay(year,month+delta,1);if(d.getUTCFullYear()<1||d.getUTCFullYear()>9999)return;year=d.getUTCFullYear();month=d.getUTCMonth()+1;render();}
    function render(focus=''){
      yearInput.value=year;monthInput.value=month;warning.textContent='';grid.replaceChildren();
      for(const text of ['일','월','화','수','목','금','토']){const h=el('span',text,'calendar-weekday');h.setAttribute('role','columnheader');grid.append(h);}
      const first=utcDay(year,month,1),offset=first.getUTCDay();
      for(let i=0;i<42;i++){
        const d=utcDay(year,month,i-offset+1),key=dayValue(d.getUTCFullYear(),d.getUTCMonth()+1,d.getUTCDate());
        const day=button(String(d.getUTCDate()),()=>{chosen=key;year=d.getUTCFullYear();month=d.getUTCMonth()+1;render(key);},'calendar-day');
        day.dataset.date=key;day.setAttribute('role','gridcell');day.setAttribute('aria-label',key);day.setAttribute('aria-selected',String(key===chosen));
        day.classList.toggle('outside',d.getUTCMonth()+1!==month);if(key===today)day.setAttribute('aria-current','date');
        day.disabled=d.getUTCFullYear()<1||d.getUTCFullYear()>9999||!allowed(key);day.tabIndex=key===chosen&&!day.disabled?0:-1;
        day.addEventListener('keydown',event=>{
          const shifts={ArrowLeft:-1,ArrowRight:1,ArrowUp:-7,ArrowDown:7,Home:-d.getUTCDay(),End:6-d.getUTCDay()};
          if(event.key==='PageUp'||event.key==='PageDown'){event.preventDefault();moveMonth(event.key==='PageUp'?-1:1);grid.querySelector('button:not(:disabled)')?.focus();return;}
          if(!(event.key in shifts))return;event.preventDefault();const dest=utcDay(d.getUTCFullYear(),d.getUTCMonth()+1,d.getUTCDate()+shifts[event.key]);
          const nextKey=dayValue(dest.getUTCFullYear(),dest.getUTCMonth()+1,dest.getUTCDate());if(dest.getUTCFullYear()<1||dest.getUTCFullYear()>9999||!allowed(nextKey))return;
          chosen=nextKey;year=dest.getUTCFullYear();month=dest.getUTCMonth()+1;render(nextKey);
        });grid.append(day);
      }
      if(!grid.querySelector('button[tabindex="0"]'))grid.querySelector('button:not(:disabled)')?.setAttribute('tabindex','0');
      selected.textContent='선택: '+value().replace('T',' ');
      if(focus)grid.querySelector(`[data-date="${focus}"]`)?.focus();
    }
    yearInput.addEventListener('change',()=>{const y=Number(yearInput.value);if(!Number.isInteger(y)||y<1||y>9999){yearInput.value=year;return;}year=y;render();});
    monthInput.addEventListener('change',()=>{month=Number(monthInput.value);render();});
    [hour,minute].forEach(control=>control.addEventListener('change',()=>selected.textContent='선택: '+value().replace('T',' ')));
    dialog.append(top,navigation,grid,time,selected,warning,actions);
    if(input.min||input.max)dialog.append(el('small',(input.min?'시작: '+input.min+' ':'')+(input.max?'종료: '+input.max:'')));
    dialog.addEventListener('cancel',event=>{event.preventDefault();close();});dialog.addEventListener('click',event=>{if(event.target===dialog)close();});
    document.body.append(dialog);render();dialog.showModal();grid.querySelector('button[tabindex="0"]')?.focus();
  }
  input.addEventListener('input',validate);input.addEventListener('change',validate);
  input.addEventListener('keydown',event=>{if(event.key==='F4'||(event.altKey&&event.key==='ArrowDown')){event.preventDefault();event.stopImmediatePropagation();show();}},true);
  new MutationObserver(validate).observe(input,{attributes:true,attributeFilter:['disabled','readonly','min','max','required']});
  const api={show,validate,refresh:validate};instances.set(input,api);validate();return api;
}
