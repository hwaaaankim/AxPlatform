import {
  request
}
from './request.js?v=0.6.0';
const form = document.getElementById('member-search');
const message = document.getElementById('request-status');
let controller;
let sequence = 0;
async function update(url, push) {
  controller?.abort();
  controller = new AbortController();
  const current = ++sequence;
  message.textContent = '목록을 조회하고 있습니다.';
  try {
    const params = new URL(url, location.origin).search;
    const html = await request('/fragments/admin/members' + params, {
      signal: controller.signal, format: 'html'
    }
    );
    if (current !== sequence) return;
    // Only the authenticated, escaped server fragment is accepted. AI/user HTML never enters this path.
    const parsed = new DOMParser().parseFromString(html, 'text/html');
    const replacement = parsed.getElementById('member-results');
    if (!replacement || parsed.querySelector('script,iframe,object')) throw new Error('목록 응답을 확인해 주세요.');
    document.getElementById('member-results').replaceWith(document.importNode(replacement, true));
    if (push) history.pushState( {
    }
    , '', '/admin/members' + params);
    message.textContent = '목록을 갱신했습니다.';
  }
  catch (error) {
    if (error.name === 'AbortError' || current !== sequence) return;
    message.textContent = error.status === 401 ? '로그인이 만료되었습니다. 다시 로그인해 주세요.' : error.message;
  }
}
form.addEventListener('submit', event => {
  event.preventDefault();
  const query = new URLSearchParams(new FormData(form));
  update('/admin/members?' + query, true);
}
);
document.getElementById('main').addEventListener('click', event => {
  const link = event.target.closest('#member-results .pagination a');
  if (!link || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
  event.preventDefault();
  update(link.href, true);
}
);
window.addEventListener('popstate', () => {
  const p = new URLSearchParams(location.search);
  for (const key of ['query','status','method','type']) form.elements[key].value = p.get(key) || ( {
    query:'',status:'ALL',method:'ALL',type:'CUSTOMER'
  }
  [key]);
  update(location.href, false);
}
);
window.addEventListener('pagehide', () => controller?.abort(), {
  once: true
}
);

document.getElementById('member-search-reset').addEventListener('click',()=>{for(const [key,value] of Object.entries({query:'',status:'ALL',method:'ALL',type:'CUSTOMER'}))form.elements[key].value=value;update('/admin/members',true);});
