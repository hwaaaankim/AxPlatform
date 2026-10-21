import {enhanceDateInput} from './date-picker.js?v=0.6.0';
import {
  request
}
from './request.js?v=0.6.0';
import {
  el,button,message,busy,whenText
}
from './ui.js?v=0.6.0';
const root=document.getElementById('member-detail'),id=root.dataset.memberId,form=document.getElementById('member-edit'),status=document.getElementById('detail-status');
let detail;
async function load() {
  detail=await request('/api/admin/members/'+id);
  const a=detail.account,admin=a.account_type==='SYSTEM_ADMIN';
  document.getElementById('member-heading').textContent=a.display_name;
  document.getElementById('member-meta').textContent=`#${a.id} · ${detail.loginMethod} · ${a.status} · 가입 ${whenText(a.created_at)}`;
  form.querySelector('#name').value=a.display_name;
  form.querySelector('#email').value=a.contact_email||'';
  form.querySelector('#phone').value=a.phone_e164||'';
  form.querySelector('#status').value=a.status;
  form.querySelectorAll('[data-customer-field]').forEach(x=>x.hidden=admin);
  form.querySelector('#reason').value='';
  const host=document.getElementById('member-workspaces');
  host.replaceChildren();
  for(const w of detail.workspaces) {
    const box=el('div','','card subtle');
    box.append(el('h3',w.name),el('p',`상품 ${w.plan_code} · 현재 권한 ${w.kind==='ADMIN_TEST'?'관리자 시험':w.effective_tier}`));
    if(w.kind==='ADMIN_TEST') {
      box.append(el('p','관리자 본인 시험 공간은 고객 등급을 배정하지 않습니다.','muted'));
      host.append(box);
      continue;
    }
    const tier=document.createElement('select');
    tier.setAttribute('aria-label','권한 등급');
    for(const t of ['', 'GUEST','TIER1','TIER2','TIER3']) {
      const o=el('option',t||'개별 지정 해제 · 상품 등급 사용');
      o.value=t;
      tier.append(o);
    }
    tier.value=w.manual_tier||'';
    const expiry=document.createElement('input');
    expiry.type='datetime-local';
    expiry.setAttribute('aria-label','지정 만료 일시');
    let originalExpiry=null;
    if(w.manual_expires){const raw=String(w.manual_expires),d=new Date(/[zZ]|[+-]\d{2}:?\d{2}$/.test(raw)?raw:raw+'Z');if(Number.isFinite(d.getTime())){originalExpiry=d.toISOString();const local=new Date(d.getTime()-d.getTimezoneOffset()*60000);expiry.value=local.toISOString().slice(0,16);}}

    const initialExpiryValue=expiry.value;
    const why=document.createElement('input');
    why.maxLength=500;
    why.placeholder='등급 변경 사유';
    why.setAttribute('aria-label','등급 변경 사유');
    const save=button('등급 적용',async()=> {
      try {
        if(!expiry.reportValidity())return;
        if(!why.value.trim())throw new Error('변경 사유를 입력해 주세요.');
        await busy(box,async()=> {
          await request(`/api/admin/workspaces/${w.id}/tier`, {
            method:'POST',body: {
              tier:tier.value,expiresAt:expiry.value?(expiry.value===initialExpiryValue&&originalExpiry?originalExpiry:new Date(expiry.value).toISOString()):'',workspaceVersion:w.version,reason:why.value
            }
          }
          );
          await load();
          message(status,'개별 권한 등급을 적용했습니다.');
        }
        );
      }
      catch(e) {
        message(status,e.message,true);
      }
    }
    ,'button primary');
    box.append(el('p',`개별 지정: ${w.manual_tier||'없음'} · 만료 ${whenText(w.manual_expires)}`,'muted'),el('label','권한 등급'),tier,el('label','만료 일시 · 비우면 계속 유지'),expiry,el('label','변경 사유'),why,save);
    enhanceDateInput(expiry);
    const histories=el('details');
    histories.append(el('summary','등급 변경 이력'));
    histories.addEventListener('toggle',async()=> {
      if(histories.open&&!histories.dataset.loaded) {
        try {
          const rows=await request(`/api/admin/workspaces/${w.id}/tier/history`);
          for(const row of rows)histories.append(el('p',`${whenText(row.created_at)} · ${row.action} ${row.tier_code||''} · ${row.reason}`));
          if(!rows.length)histories.append(el('p','변경 이력이 없습니다.'));
          histories.dataset.loaded='true';
        }
        catch(e) {
          message(status,e.message,true);
        }
      }
    }
    );
    box.append(histories);
    host.append(box);
  }
  if(!detail.workspaces.length)host.append(el('p','소유한 공간이 없습니다.'));
  const history=document.getElementById('member-history');
  history.replaceChildren();
  for(const h of detail.history) {
    const x=el('div','','history-entry');
    x.append(el('strong',`${whenText(h.created_at)} · ${h.action}`),el('p',h.reason));
    const d=el('details');
    d.append(el('summary','변경 전·후'),el('pre',`이전: ${h.before_json}\n이후: ${h.after_json}`));
    x.append(d);
    history.append(x);
  }
  if(!detail.history.length)history.append(el('p','변경 이력이 없습니다.','muted'));
}
form.addEventListener('submit',async e=> {
  e.preventDefault();
  try {
    if(form.querySelector('#status').value==='WITHDRAWN'&&!confirm('이 계정을 탈퇴 상태로 변경하시겠습니까? 업무 데이터는 보관되고 로그인은 중단됩니다.'))return;
    await busy(form,async()=> {
      await request('/api/admin/members/'+id, {
        method:'PATCH',body: {
          name:form.querySelector('#name').value,email:form.querySelector('#email').value,phone:form.querySelector('#phone').value,status:form.querySelector('#status').value,version:detail.account.version,reason:form.querySelector('#reason').value
        }
      }
      );
      await load();
      message(status,'회원 변경을 적용했습니다.');
    }
    );
  }
  catch(e) {
    message(status,e.message,true);
  }
}
);
document.getElementById('member-reload').addEventListener('click',()=> {
  if(confirm('입력 중인 내용을 버리고 최신 정보를 불러올까요?'))load().catch(e=>message(status,e.message,true));
}
);
load().catch(e=>message(status,e.message,true));
