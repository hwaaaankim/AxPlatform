import {el} from './ui.js?v=0.10.1';
export const FORMAT='AX_DATA_VIEW_1';
export const operations={COUNT:'행 개수',SUM:'합계',AVG:'평균',MIN:'최솟값',MAX:'최댓값',DISTINCT:'고유값 수'};
export const filterOps={EQ:'같음',NE:'다름',CONTAINS:'포함',GT:'초과',GTE:'이상',LT:'미만',LTE:'이하',EMPTY:'비어 있음',NOT_EMPTY:'값 있음'};
export const blankView=(sheetId)=>({format:FORMAT,mode:'TABLE',name:'원본 테이블',sheetId,columns:[],filters:[],dimensions:[],metrics:[],sorts:[],chart:'TABLE',chartLimit:20,search:'',searchColumns:[],filterColumns:[],pageSize:100,density:'NORMAL',wrap:false,defaultView:false});
export function normalizeView(c,sheetId){if(c.format===FORMAT)return {...blankView(c.sheetId||sheetId),...structuredClone(c)};return {...blankView(c.sheetId||sheetId),mode:'REPORT',name:c.name||'저장한 보고서',dimensions:c.groupColumn?[c.groupColumn]:[],metrics:[{column:c.valueColumn||'',operation:c.operation||'COUNT'}]};}
export function suggestions(profile,sheetId){
 const text=profile.filter(c=>!c.numeric&&c.samples?.length),nums=profile.filter(c=>c.numeric),party=text.find(c=>/거래처|업체|고객|회사/.test(c.label))||text[0],date=profile.find(c=>/월|일자|날짜|일시/.test(c.label))||text[0],value=nums.find(c=>/금액|수량|중량|매출|투입|입고|출고|단가/.test(c.label))||nums[0],base=blankView(sheetId),out=[];
 if(party)out.push({title:`${party.label} ‘${party.samples[0]}’만 조회`,description:'해당 값과 같은 원본 행을 표시합니다.',config:{...base,name:party.label+'별 조회',filters:[{column:party.id,operator:'EQ',value:party.samples[0]}]}});
 if(date&&value)out.push({title:`${date.label}별 ${value.label} 합계`,description:'같은 기준의 행을 묶어 합계를 비교합니다.',config:{...base,mode:'REPORT',name:date.label+'별 '+value.label+' 합계',dimensions:[date.id],metrics:[{column:value.id,operation:'SUM'}],chart:'BAR'}});
 if(party)out.push({title:`${party.label}별 건수`,description:'각 항목에 해당하는 데이터 행 수입니다.',config:{...base,mode:'REPORT',name:party.label+'별 건수',dimensions:[party.id],metrics:[{column:'',operation:'COUNT'}],chart:'BAR'}});
 return out;
}
const palette=['#5146dc','#079b91','#d87d16','#d54976','#4479d9','#7c54aa','#438046','#ad4c33'];
function svg(tag,attrs={}){const n=document.createElementNS('http://www.w3.org/2000/svg',tag);for(const[k,v]of Object.entries(attrs))n.setAttribute(k,String(v));return n;}
export function chart(result,type){
 const wrap=el('div','','dc-chart');if(type==='TABLE')return wrap;
 const rows=result.chartRows,dim=result.config.dimensions.length,metrics=result.columns.slice(dim),labels=rows.map(r=>r.slice(0,dim).map(v=>v||'(빈 값)').join(' / ')||'전체');
 const heading=el('p',type==='LINE'?'현재 보고서 정렬 순서로 연결합니다. 시간 흐름이면 날짜 기준 오름차순을 선택하세요.':type==='PIE'?'선택한 집계값이 전체에서 차지하는 비율입니다.':'항목별 집계값을 비교합니다. 음수는 기준선 왼쪽에 표시됩니다.','help-line');wrap.append(heading);
 const legend=el('div','','dc-legend');metrics.forEach((m,i)=>{const n=el('span',m.label);n.style.setProperty('--series-color',palette[i%palette.length]);legend.append(n);});wrap.append(legend);
 if(type==='BAR'){
  const max=Math.max(1,...rows.flatMap(r=>r.slice(dim).map(Number).filter(Number.isFinite)).map(Math.abs));
  rows.forEach((r,ri)=>{const group=el('div','','dc-bar-group');group.append(el('strong',labels[ri]));metrics.forEach((m,i)=>{const raw=r[dim+i],v=Number(raw),row=el('div','','dc-bar-row'),track=el('div','','dc-bar-track'),fill=el('i');fill.style.background=palette[i%palette.length];fill.style.width=(raw===''?0:Math.abs(v)/max*50)+'%';fill.style.left=v<0?(50-Math.abs(v)/max*50)+'%':'50%';track.append(fill);row.append(el('span',m.label),track,el('b',raw===''?'값 없음':v.toLocaleString('ko-KR',{maximumFractionDigits:6})));group.append(row);});wrap.append(group);});return wrap;
 }
 const canvas=svg('svg',{viewBox:'0 0 900 320',role:'img','aria-label':type==='LINE'?'보고서 꺾은선 그래프':'보고서 비율 그래프'});wrap.append(canvas);
 if(type==='LINE'){
  const ns=rows.flatMap(r=>r.slice(dim).filter(v=>v!=='').map(Number)).filter(Number.isFinite),min=Math.min(0,...ns),max=Math.max(1,...ns),y=v=>280-(v-min)/(max-min)*240,x=i=>70+i*790/Math.max(1,rows.length-1);
  for(let n=0;n<=4;n++){const value=min+(max-min)*n/4,line=svg('line',{x1:70,x2:860,y1:y(value),y2:y(value),stroke:'#e4e7f0'}),text=svg('text',{x:60,y:y(value)+4,'text-anchor':'end','font-size':11});text.textContent=value.toLocaleString('ko-KR',{maximumFractionDigits:1});canvas.append(line,text);}
  metrics.forEach((m,mi)=>{let points=[];const flush=()=>{if(points.length)canvas.append(svg('polyline',{points:points.join(' '),fill:'none',stroke:palette[mi%palette.length],'stroke-width':2}));points=[];};rows.forEach((r,i)=>{if(r[dim+mi]===''){flush();return;}const val=Number(r[dim+mi]);points.push(x(i)+','+y(val));const dot=svg('circle',{cx:x(i),cy:y(val),r:3,fill:palette[mi%palette.length]}),t=svg('title');t.textContent=`${labels[i]} · ${m.label}: ${val}`;dot.append(t);canvas.append(dot);});flush();});labels.forEach((label,i)=>{if(i%Math.max(1,Math.ceil(labels.length/8))&&i!==labels.length-1)return;const t=svg('text',{x:x(i),y:305,'text-anchor':'middle','font-size':10});t.textContent=label.slice(0,16);canvas.append(t);});
 }else{
  const values=rows.map(r=>Number(r[dim])),sum=values.reduce((a,b)=>a+b,0);let at=-Math.PI/2;values.forEach((v,i)=>{if(v<=0)return;const angle=v/sum*2*Math.PI,end=at+angle;const arc=angle>=2*Math.PI-1e-10?svg('circle',{cx:200,cy:160,r:120,fill:palette[i%palette.length]}):svg('path',{d:`M200,160 L${200+120*Math.cos(at)},${160+120*Math.sin(at)} A120,120 0 ${angle>Math.PI?1:0},1 ${200+120*Math.cos(end)},${160+120*Math.sin(end)} Z`,fill:palette[i%palette.length]});const t=svg('title');t.textContent=`${labels[i]}: ${v} (${(v/sum*100).toFixed(1)}%)`;arc.append(t);canvas.append(arc);const label=svg('text',{x:370,y:25+i*24,fill:palette[i%palette.length],'font-size':13});label.textContent=`${labels[i].slice(0,32)} · ${(v/sum*100).toFixed(1)}%`;canvas.append(label);at=end;});
 }return wrap;
}
