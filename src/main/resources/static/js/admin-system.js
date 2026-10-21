import {request} from './request.js?v=0.6.0';
import {el,message} from './ui.js?v=0.6.0';
import {diagnosticDetails} from './ai-diagnostic.js?v=0.6.0';
const check=document.getElementById('ai-connection-check'),status=document.getElementById('ai-connection-status'),result=document.getElementById('ai-connection-result');
let timer;
check.addEventListener('click',async()=>{
  clearInterval(timer);check.disabled=true;result.replaceChildren();message(status,'OpenAI 연결을 확인하고 있습니다.');
  try{
    const report=await request('/api/admin/ai/diagnostics',{method:'POST',body:{}});
    message(status,report.ok?'입력 토큰 확인 성공':'연결 확인 실패',!report.ok);
    result.append(el('p',report.message,report.ok?'notice success':'notice error'));
    if(!report.ok)result.append(diagnosticDetails(report.diagnostic,report.errorCode));
    result.append(el('p',`확인 모델: ${report.model} · 생성 요청 0건`,'muted'));
  }catch(error){message(status,error.message,true);}
  finally{
    let remaining=30;check.textContent=`${remaining}초 뒤 다시 확인`;
    timer=setInterval(()=>{remaining--;check.textContent=remaining>0?`${remaining}초 뒤 다시 확인`:'AI 연결 확인';if(remaining<=0){clearInterval(timer);check.disabled=false;}},1000);
  }
});
window.addEventListener('pagehide',()=>clearInterval(timer),{once:true});
