import {el} from './ui.js?v=0.9.1';

export function diagnosticDetails(info={},errorCode=''){
  const details=el('details','','ai-diagnostic'),list=el('dl','','diagnostic-grid');
  details.append(el('summary','오류 확인 정보'));
  const rows=[['오류 구분',errorCode],['실패 단계',info.stage==='INPUT_TOKENS'?'입력 토큰 사전 확인':info.stage==='GENERATION'?'AI 생성 요청':''],['HTTP 상태',info.httpStatus],['OpenAI 오류 코드',info.providerCode],['오류 유형',info.providerType],['확인할 설정',info.parameter],['모델',info.model],['요청 경로',info.endpoint],['요청 식별번호',info.requestId]];
  for(const [title,value] of rows)if(value!==undefined&&value!==null&&value!==''){list.append(el('dt',title),el('dd',String(value)));}
  details.append(list,el('p','관리자에게 이 정보를 전달하면 오류를 확인할 수 있습니다. 키나 입력 내용은 포함하지 않습니다.','muted'));
  return details;
}
