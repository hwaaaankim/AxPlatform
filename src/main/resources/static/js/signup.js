import {
  request
}
from './request.js?v=0.9.1';
import {
  el,message,busy
}
from './ui.js?v=0.9.1';
const root=document.getElementById('signup-root'),form=document.getElementById('signup-form'),status=document.getElementById('auth-status');
const social=root.dataset.social==='true',trusted=root.dataset.trustedEmail==='true',initialEmail=document.getElementById('email').value.trim().toLowerCase();
let step=0;
const proofs= {
  email:trusted? {
    id:'',verified:true,target:initialEmail
  }
  :null,phone:null
}
;
const timers= {
}
;
const target=k=>document.getElementById(k).value.trim();
const canonical=(k,v)=>k==='email'?v.toLowerCase():v.replace(/[ ()-]/g,'').replace(/^010/,'+8210');
const isVerified=k=>proofs[k]?.verified&&proofs[k].target===canonical(k,target(k));
function refresh() {
  if(trusted&&target('email').toLowerCase()===initialEmail)proofs.email={id:'',verified:true,target:initialEmail};
  for(const k of ['email','phone']) {
    document.querySelector(`[data-verified="${k}"]`).textContent=isVerified(k)?'인증 완료':'인증이 필요합니다.';
  }
  document.getElementById('email-otp').hidden=trusted&&target('email').toLowerCase()===initialEmail;
}
function go(n) {
  step=n;
  document.querySelectorAll('[data-step]').forEach(x=>x.hidden=Number(x.dataset.step)!==n);
  document.querySelectorAll('[data-step-label]').forEach(x=>x.classList.toggle('current',Number(x.dataset.stepLabel)===n));
  document.getElementById('signup-prev').hidden=n===0;
  document.getElementById('signup-next').hidden=n===2;
  document.getElementById('signup-submit').hidden=n!==2;
  message(status,'');
  if(n===2) {
    const dl=document.getElementById('signup-summary');
    dl.replaceChildren();
    for(const [a,b] of [['가입 방식',social?'SNS':'이메일'],['이름',target('name')],['이메일',target('email')],['휴대전화',target('phone')]])dl.append(el('dt',a),el('dd',b));
  }
}
for(const k of ['email','phone']) {
  document.getElementById(k).addEventListener('input',()=> {
    if(proofs[k]&&proofs[k].target!==canonical(k,target(k)))proofs[k]=null;
    refresh();
  }
  );
  document.querySelector(`[data-send="${k}"]`).addEventListener('click',async()=> {
    try {
      if(!target(k))throw new Error('수신 정보를 입력해 주세요.');
      await busy(form,async()=> {
        const result=await request('/api/auth/verification', {
          method:'POST',body: {
            purpose:k==='email'?'SIGNUP_EMAIL':'SIGNUP_PHONE',target:target(k)
          }
        }
        );
        proofs[k]= {
          id:result.id,verified:false,target:canonical(k,target(k))
        }
        ;
        const until=Date.now()+result.expiresIn*1000,resend=Date.now()+result.resendAfter*1000;
        clearInterval(timers[k]);
        const tick=()=> {
          const remaining=Math.max(0,Math.ceil((until-Date.now())/1000)),wait=Math.max(0,Math.ceil((resend-Date.now())/1000));
          document.querySelector(`[data-timer="${k}"]`).textContent=remaining?`유효 ${Math.floor(remaining/60)}:${String(remaining%60).padStart(2,'0')} · 재발송 ${wait}초`:'인증번호가 만료되었습니다.';
          document.querySelector(`[data-send="${k}"]`).disabled=wait>0;
          if(!remaining)clearInterval(timers[k]);
        }
        ;
        timers[k]=setInterval(tick,1000);
        setTimeout(tick,0);
        message(status,'인증번호 발송을 요청했습니다. 받은 6자리 번호를 입력해 주세요.');
        refresh();
      }
      );
    }
    catch(e) {
      message(status,e.message,true);
    }
  }
  );
  document.querySelector(`[data-confirm="${k}"]`).addEventListener('click',async()=> {
    try {
      if(!proofs[k]?.id)throw new Error('인증번호를 먼저 받아 주세요.');
      const code=document.getElementById(k+'-code').value;
      if(!/^\d{6}$/.test(code))throw new Error('6자리 인증번호를 입력해 주세요.');
      await busy(form,async()=> {
        await request(`/api/auth/verification/${encodeURIComponent(proofs[k].id)}/confirm`, {
          method:'POST',body: {
            code
          }
        }
        );
        proofs[k].verified=true;
        clearInterval(timers[k]);
        document.querySelector(`[data-timer="${k}"]`).textContent='가입을 30분 이내에 완료해 주세요.';
        message(status,'인증을 완료했습니다.');
        refresh();
      }
      );
    }
    catch(e) {
      message(status,e.message,true);
    }
  }
  );
}
document.getElementById('signup-prev').addEventListener('click',()=>go(step-1));
document.getElementById('signup-next').addEventListener('click',()=> {
  try {
    if(step===0) {
      if(!document.getElementById('email').checkValidity())throw new Error('이메일 형식을 확인해 주세요.');
      if(!isVerified('email'))throw new Error('이메일 인증을 완료해 주세요.');
      if(!social) {
        const p=document.getElementById('signup-password').value;
        if(p.length<12||p.length>64||new TextEncoder().encode(p).length>72)throw new Error('비밀번호는 12~64자, UTF-8 기준 72바이트 이내로 입력하세요.');
        if(p!==document.getElementById('confirmation').value)throw new Error('비밀번호 확인이 다릅니다.');
      }
    }
    else {
      if(!target('name'))throw new Error('이름을 입력해 주세요.');
      if(!isVerified('phone'))throw new Error('문자 인증을 완료해 주세요.');
    }
    go(step+1);
  }
  catch(e) {
    message(status,e.message,true);
  }
}
);
form.addEventListener('submit',async e=> {
  e.preventDefault();
  try {
    if(step!==2)return;
    if(!isVerified('email')||!isVerified('phone'))throw new Error('이메일·휴대전화 인증을 확인해 주세요.');
    if(!document.getElementById('agree-terms').checked||!document.getElementById('agree-privacy').checked)throw new Error('필수 약관에 동의해 주세요.');
    await busy(form,async()=> {
      const result=await request('/api/auth/signup', {
        method:'POST',body: {
          email:target('email'),password:social?'':document.getElementById('signup-password').value,confirmation:social?'':document.getElementById('confirmation').value,name:target('name'),phone:target('phone'),emailProof:proofs.email?.id||'',phoneProof:proofs.phone?.id||'',terms:true,privacy:true
        }
      }
      );
      location.assign(result.next);
    }
    );
  }
  catch(e) {
    message(status,e.message,true);
  }
}
);
if(root.dataset.registration!=='true') {
  form.querySelectorAll('input,button').forEach(x=>x.disabled=true);
}
else {
  document.querySelector('[data-send="email"]').disabled=root.dataset.emailReady!=='true';
  document.querySelector('[data-send="phone"]').disabled=root.dataset.smsReady!=='true';
}
refresh();
window.addEventListener('pagehide',()=>Object.values(timers).forEach(clearInterval));
