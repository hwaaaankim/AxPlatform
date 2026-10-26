import {initFormControls} from './form-controls.js?v=0.10.1';
import {initPageLoading} from './loading.js?v=0.10.1';
import { initTutorial } from './tutorial.js?v=0.10.1';
const paths={home:'M3 10 12 3l9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z',table:'M3 3h18v18H3ZM3 9h18M9 3v18',document:'M6 3h9l4 4v14H6ZM14 3v5h5M9 12h7M9 16h7',slides:'M3 4h18v13H3ZM8 21l4-4 4 4M7 8h5M7 12h9',users:'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M20 21v-3a4 4 0 0 0-3-4M17 3a4 4 0 0 1 0 8',calendar:'M3 5h18v16H3ZM7 2v6M17 2v6M3 11h18',grid:'M3 3h7v7H3ZM14 3h7v7h-7ZM3 14h7v7H3ZM14 14h7v7h-7Z',shield:'M12 3 3 7v6c0 4 5 7 9 9 4-2 9-5 9-9V7ZM8 12l3 3 5-6',lock:'M5 10h14v11H5ZM8 10V6a4 4 0 0 1 8 0v4',settings:'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2'};
export function decorateIcons(root=document){root.querySelectorAll('[data-icon]:not([data-icon-ready])').forEach(n=>{const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('fill','none');svg.setAttribute('stroke','currentColor');svg.setAttribute('stroke-width','1.6');svg.setAttribute('stroke-linecap','round');svg.setAttribute('stroke-linejoin','round');svg.setAttribute('aria-hidden','true');svg.classList.add('icon');const p=document.createElementNS(svg.namespaceURI,'path');p.setAttribute('d',paths[n.dataset.icon]||paths.grid);svg.append(p);n.prepend(svg);n.dataset.iconReady='true';});}
function boot(){
 if(document.documentElement.dataset.axShell==='ready')return;document.documentElement.dataset.axShell='ready';
 const main=document.querySelector('main');if(main&&!main.id)main.id='main-content';const skip=document.querySelector('.skip-link');if(main&&skip)skip.href='#'+main.id;
 decorateIcons();
 const scope=document.getElementById('draft-root')?.dataset.businessId||new URLSearchParams(location.search).get('businessId');if(scope)document.querySelectorAll('.sidebar [data-workflow]').forEach(a=>{const u=new URL(a.href);u.searchParams.set('businessId',scope);a.href=u.pathname+u.search;});
 const wf=document.getElementById('draft-root')?.dataset.workflow||new URLSearchParams(location.search).get('workflow')||'ALL';
 document.querySelectorAll('.sidebar a').forEach(a=>{const same=a.dataset.workflow? a.dataset.workflow===wf:(location.pathname===a.getAttribute('href'));if(same)a.setAttribute('aria-current','page');});
 const menu=document.querySelector('[data-menu-toggle]'),backdrop=document.querySelector('.sidebar-backdrop'),sidebar=document.querySelector('.sidebar');
 const close=()=>{document.body.classList.remove('menu-open');menu?.setAttribute('aria-expanded','false');menu?.setAttribute('aria-label','메뉴 열기');if(backdrop)backdrop.hidden=true;};
 menu?.addEventListener('click',()=>{const open=!document.body.classList.contains('menu-open');document.body.classList.toggle('menu-open',open);menu.setAttribute('aria-expanded',String(open));menu.setAttribute('aria-label',open?'메뉴 닫기':'메뉴 열기');if(backdrop)backdrop.hidden=!open;if(open)sidebar?.querySelector('a')?.focus();});
 backdrop?.addEventListener('click',()=>{close();menu?.focus();});
 sidebar?.querySelectorAll('a').forEach(a=>a.addEventListener('click',close));
 window.addEventListener('resize',()=>{if(!window.matchMedia('(max-width: 950px)').matches)close();});
 document.addEventListener('keydown',e=>{if(e.key==='Escape'&&document.body.classList.contains('menu-open')){close();menu?.focus();}});
 document.querySelectorAll('input[type=password]').forEach(input=>{if(input.parentElement.classList.contains('password-wrap'))return;const wrap=document.createElement('div');wrap.className='password-wrap';input.before(wrap);wrap.append(input);const b=document.createElement('button');b.type='button';b.className='password-toggle';b.textContent='보기';b.setAttribute('aria-label','비밀번호 표시');b.addEventListener('click',()=>{const show=input.type==='password';input.type=show?'text':'password';b.textContent=show?'숨김':'보기';b.setAttribute('aria-label',show?'비밀번호 숨기기':'비밀번호 표시');b.setAttribute('aria-pressed',String(show));});wrap.append(b);});
 initFormControls();
 initPageLoading();
 initTutorial();
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',boot,{once:true});else boot();
