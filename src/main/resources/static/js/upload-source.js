import {request} from './request.js?v=0.6.0';
const keys=new WeakMap();
export function uploadSource(file,params){if(!keys.has(file))keys.set(file,crypto.randomUUID());const body=new FormData();body.append('file',file);return request('/api/assets?'+new URLSearchParams({...params,requestKey:keys.get(file)}),{method:'POST',body});}
