export function watch(businessId, {
  onChange,onPolicy,onResync,onStatus
}
) {
  const stream=new EventSource('/api/events?businessId='+encodeURIComponent(businessId));
  let disposed=false,failures=0;
  const versions=new Map();
  stream.addEventListener('ready',()=> {
    failures=0;
    onStatus?.('실시간 연결됨');
    onResync?.();
  }
  );
  stream.addEventListener('policy',e=> {
    try {
      onPolicy?.(JSON.parse(e.data));
    }
    catch {
      onStatus?.('권한 응답을 다시 확인해 주세요.');
    }
  }
  );
  stream.addEventListener('change',e=> {
    try {
      const data=JSON.parse(e.data);
      const key=data.event_type+':'+data.object_id,version=Number(data.object_version);
      if((versions.get(key)??-1)>=version)return;
      versions.set(key,version);
      onChange?.(data);
    }
    catch {
      onResync?.();
    }
  }
  );
  stream.addEventListener('revoked',()=> {
    stream.close();
    onStatus?.('접속 범위·권한 확인이 필요합니다.');
    onPolicy?.(null);
  }
  );
  stream.onerror=()=> {
    if(disposed)return;
    failures++;
    onStatus?.('연결을 다시 시도하고 있습니다.');
    if(failures>3) {
      stream.close();
      onStatus?.('실시간 연결 중단 · 화면 갱신으로 최신 내용을 확인하세요.');
      onPolicy?.(null);
    }
  }
  ;
  const timer=setInterval(()=>onPolicy?.(null),60000);
  return ()=> {
    disposed=true;
    clearInterval(timer);
    stream.close();
  }
  ;
}
