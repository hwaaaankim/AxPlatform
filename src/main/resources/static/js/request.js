export class RequestError extends Error {
  constructor(message,status,code,details= {
  }
  ) {
    super(message);
    this.status=status;
    this.code=code;
    this.details=details;
  }
}
export async function request(url, {
  method='GET',body,signal,format='json',idempotencyKey
}
= {
}
) {
  const headers= {
    Accept:format==='html'?'text/html':'application/json'
  }
  ;
  if(!['GET','HEAD','OPTIONS'].includes(method.toUpperCase())) {
    const token=document.querySelector('meta[name="_csrf"]')?.content;
    const name=document.querySelector('meta[name="_csrf_header"]')?.content;
    if(!token||!name)throw new RequestError('인증값을 확인하지 못했습니다. 화면을 다시 열어 주세요.',403,'CSRF_MISSING');
    headers[name]=token;
  }
  if(idempotencyKey)headers['Idempotency-Key']=idempotencyKey;
  if(body!==undefined&&!(body instanceof FormData)) {
    headers['Content-Type']='application/json';
    body=JSON.stringify(body);
  }
  const response=await fetch(url, {
    method,headers,body,signal,credentials:'same-origin',redirect:'error'
  }
  );
  const type=response.headers.get('content-type')||'';
  if(!response.ok) {
    const detail=type.includes('application/json')?await response.json(): {
    }
    ;
    throw new RequestError(detail.message||'요청을 처리하지 못했습니다.',response.status,detail.code||'REQUEST_FAILED',detail.details|| {
    }
    );
  }
  if(format==='html') {
    if(!type.includes('text/html'))throw new RequestError('화면 응답의 형식을 확인하지 못했습니다.',response.status,'CONTENT_TYPE');
    return response.text();
  }
  if(response.status===204)return null;
  if(!type.includes('application/json'))throw new RequestError('자료 응답의 형식을 확인하지 못했습니다.',response.status,'CONTENT_TYPE');
  return response.json();
}
