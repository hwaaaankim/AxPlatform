const TTL=24*60*60*1000;
export function editState(account,business,draft) {
  const key=`ax:work-input:${account}:${business}:${draft||'new'}`;
  return {
    read() {
      try {
        const value=JSON.parse(sessionStorage.getItem(key)||'null');
        if(value&&Date.now()-value.at<TTL)return value;
        sessionStorage.removeItem(key);
      }
      catch {
      }
      return null;
    }
    ,write(value) {
      try {
        sessionStorage.setItem(key,JSON.stringify( {
          ...value,at:Date.now()
        }
        ));
      }
      catch {
      }
    }
    ,clear() {
      try {
        sessionStorage.removeItem(key);
      }
      catch {
      }
    }
  }
  ;
}
