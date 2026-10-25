import {request} from './request.js?v=0.9.1';
import {el,button,message} from './ui.js?v=0.9.1';
import {enhanceFileInput,fileSize} from './file-picker.js?v=0.9.1';

export const locallyMissing = row => row.file_exists === false;
export function localLabel(row){
  return row.local_status === 'INVALID' ? '이 PC의 원본이 다릅니다 · 교체 필요'
    : row.local_status === 'UNAVAILABLE' ? '이 PC에서 원본을 읽을 수 없습니다'
    : locallyMissing(row) ? '이 PC에 파일 없음' : '이 PC에서 사용 가능';
}
export function assetPreview(row,admin=false){
  const box=el('div','','asset-thumbnail');
  box.setAttribute('role','img');box.setAttribute('aria-label',row.original_name);
  const empty=()=>{box.replaceChildren(el('span','파일 없음'));box.classList.add('is-missing');box.setAttribute('aria-label',row.original_name+' · '+localLabel({...row,file_exists:false}));};
  if(locallyMissing(row)){empty();return box;}
  if(row.media_type?.startsWith('image/')){
    const img=el('img');img.alt=row.original_name;img.loading='lazy';
    img.addEventListener('error',empty,{once:true});
    img.src=`${admin?'/api/admin/files':'/api/my/files'}/${encodeURIComponent(row.id)}/preview?v=${row.version}`;
    box.append(img);
  }else box.append(el('span',row.original_name?.split('.').pop().slice(0,5).toUpperCase()||'FILE'));
  return box;
}

export function replaceAsset(row,{admin=false,onReplaced=async()=>{}}={}){
  const prefix=admin?'/api/admin/files':'/api/my/files',dialog=el('dialog','','source-dialog asset-replace-dialog');
  const heading=el('h2','원본 파일 교체'),description=el('p',`${row.original_name} · ${fileSize(Number(row.byte_size))}`),form=el('form'),input=el('input'),notice=el('p','','help-line'),references=el('p','연결된 작업을 확인하고 있습니다.','muted');
  heading.id='replace-heading';dialog.setAttribute('aria-labelledby',heading.id);notice.setAttribute('role','status');
  input.type='file';input.accept=row.media_type?.startsWith('image/')?'.png,.jpg,.jpeg':row.media_type==='text/plain'?'.txt':'.xlsx,.csv';input.setAttribute('aria-label','교체할 원본 1개');
  const send=el('button','이 PC에 저장하고 교체','button primary');send.type='submit';send.disabled=true;
  let busy=false,ready=false;const close=button('취소',()=>{if(!busy){dialog.close();dialog.remove();}});
  const actions=el('div','','actions');actions.append(close,send);
  form.append(input,references,notice,actions);
  dialog.append(heading,description,el('p','파일 1개를 선택하세요. 자료 연결은 유지되고, 모든 PC에서 파일 이름과 원본 정보가 바뀝니다. 새 파일은 현재 PC에만 저장됩니다. 이미 만든 표는 유지됩니다. 이전 원본의 미적용 AI 제안은 만료되므로 새 원본으로 다시 검토하세요.','muted'),form);
  document.body.append(dialog);const picker=enhanceFileInput(input,{multiple:false});
  const update=()=>{send.disabled=busy||!ready||picker.files().length!==1;};input.addEventListener('ax:files-change',update);
  dialog.addEventListener('cancel',e=>{if(busy)e.preventDefault();else dialog.remove();});
  dialog.addEventListener('close',()=>{if(!busy)dialog.remove();});
  form.addEventListener('submit',async event=>{
    event.preventDefault();if(send.disabled)return;busy=true;close.disabled=true;picker.setDisabled(true);update();
    let result;
    try{
      const body=new FormData();body.append('file',picker.files()[0]);
      result=await request(`${prefix}/${encodeURIComponent(row.id)}/replace?version=${row.version}`,{method:'POST',body});
    }catch(e){message(notice,e.message+(e.status===409?' 목록을 새로고침한 뒤 다시 확인하세요.':''),true);}
    finally{busy=false;close.disabled=false;picker.setDisabled(false);update();}
    if(result){
      ready=false;send.disabled=true;picker.setDisabled(true);
      try{await onReplaced(result);dialog.close();dialog.remove();}
      catch(e){close.textContent='닫기';message(notice,'파일 교체는 저장되었습니다. 목록 갱신 실패: '+e.message+' 화면을 새로고침하세요.',true);}
    }
  });
  request(`${prefix}/${encodeURIComponent(row.id)}/impact`).then(impact=>{
    if(!dialog.isConnected)return;
    if(Number(impact.version)!==Number(row.version)){message(notice,'자료가 변경되었습니다. 닫고 목록을 새로고침하세요.',true);return;}
    if(impact.activeAi){message(notice,'이 자료를 사용하는 AI 작업이 실행 중입니다. 완료하거나 취소한 후 교체하세요.',true);return;}
    references.textContent=impact.references.length?'연결 유지: '+impact.references.map(x=>x.title).join(', '):'현재 연결된 작업이 없습니다.';
    ready=true;update();
  }).catch(e=>message(notice,e.message,true));
  dialog.showModal();close.focus();return dialog;
}
