import {createStickerEditor} from './stickers.mjs';
const $ = id => document.getElementById(id);
const storageKey = 'palette-journey-participant';
const names = { red:'빨강', orange:'주황', yellow:'노랑', green:'초록', blue:'파랑', purple:'보라', pink:'핑크', brown:'브라운', white:'화이트', black:'블랙', gray:'그레이' };
let token = null, palette = null, selected = null, revision = 0, dirty = false, pendingJoin = null, editVersion = 0;
let strokes=[], legacyRegions={}, active=null, undone=[], tool='paint';
let library=null,currentPage=null,saveTimer=null,saving=null,autoBlocked=false;
const canvas=$('drawing'), ctx=canvas.getContext('2d');
const stickerEditor=createStickerEditor({root:$('sticker-layer'),pagePointCount:()=>strokes.reduce((n,s)=>n+s.points.length,0),getColor:()=>selected,getWidth:()=>Number($('size').value),canEdit:()=>!!token&&!!currentPage&&!active,onChange:changed,onSelect:(item,message)=>{
  $('sticker-selection').hidden=!item;if(item){$('sticker-name').textContent='선택한 스티커';$('sticker-width').value=item.width;$('sticker-height').value=item.height;}
  $('undo').disabled=false;$('redo').disabled=false;if(message)status(message);
}});
function snapshot(){return {title:$('page-title').value,text:$('page-text').value,template:currentPage.template,strokes,stickers:stickerEditor.placements(),revision,legacyRegions};}
function changed(){
  if(!currentPage){status('먼저 그림일기와 페이지를 추가해주세요.');return;}
  editVersion++;dirty=true;$('save-state').textContent='자동 저장을 기다리고 있어요.';
  if(currentPage){try{localStorage.setItem('pj-draft-'+currentPage.id,JSON.stringify(snapshot()));}catch{status('이 기기의 임시 저장 공간이 부족해요. 서버 저장 상태를 확인해주세요.');}}
  clearTimeout(saveTimer);if(!autoBlocked)saveTimer=setTimeout(()=>{if(!active&&!stickerEditor.isActive()&&dirty)busy($('save'),saveCurrent);},1200);
}
function drawStroke(stroke){
  ctx.globalCompositeOperation=stroke.tool==='erase'?'destination-out':'source-over';
  ctx.strokeStyle=stroke.color;ctx.fillStyle=stroke.color;ctx.lineWidth=stroke.width;ctx.lineCap='round';ctx.lineJoin='round';
  ctx.beginPath();ctx.moveTo(...stroke.points[0]);for(const point of stroke.points.slice(1))ctx.lineTo(...point);
  if(stroke.points.length===1){ctx.arc(...stroke.points[0],stroke.width/2,0,Math.PI*2);ctx.fill();}else ctx.stroke();
}
function redraw(){
  ctx.clearRect(0,0,600,400);ctx.globalCompositeOperation='source-over';
  const paths={sky:'M5 5H595V395H5Z',sun:'M508 90 A43 43 0 1 0 422 90 A43 43 0 1 0 508 90',mountain:'M5 260L155 90L285 240L370 150L595 285V395H5Z',field:'M5 295Q150 225 300 285T595 270V395H5Z',road:'M285 280L320 280Q295 325 410 395H190Q320 320 285 280Z'};
  for(const id of ['sky','sun','mountain','field','road'])if(legacyRegions[id]){ctx.fillStyle=legacyRegions[id];ctx.fill(new Path2D(paths[id]));}
  for(const stroke of strokes)drawStroke(stroke);if(active)drawStroke(active);
  ctx.globalCompositeOperation='source-over';$('undo').disabled=!strokes.length;$('redo').disabled=!undone.length;
}
const messages = { UNAUTHORIZED:'연결이 만료되거나 종료됐어요. 새 코드로 다시 연결해주세요.', INVALID_SESSION:'연결 코드가 잘못됐거나 만료됐어요.', NO_TICKET:'사용할 티켓이 없어요.', ARTWORK_CONFLICT:'다른 기기에서 그림을 저장했어요. 현재 그림은 유지됩니다. 저장된 그림을 불러온 뒤 다시 작업해주세요.', INVALID_ARTWORK:'사용할 수 없는 색이나 그림 정보예요.' };
messages.INVALID_DIARY='제목·별명과 글 길이를 확인해주세요. 제목은 비워둘 수 없어요.';
messages.DIARY_NOT_FOUND='이 가족의 기록을 찾지 못했어요. 다시 연결해주세요.';
messages.INVALID_STICKER_PLACEMENT='보유하지 않은 스티커이거나 페이지 밖으로 벗어난 배치예요.';
function status(message) { $('status').textContent = message; }
function randomToken() { return [...crypto.getRandomValues(new Uint8Array(32))].map(n => n.toString(16).padStart(2,'0')).join(''); }
async function api(path, method='GET', body, credential=token) {
  const response = await fetch(path, { method, headers: { Authorization:`Bearer ${credential}`, 'Content-Type':'application/json' }, ...(body === undefined ? {} : { body:JSON.stringify(body) }) });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error);
  return data;
}
function error(error) {
  status(messages[error.message] ?? '요청을 완료하지 못했어요. 연결을 확인하고 다시 시도해주세요.');
  if (error.message === 'UNAUTHORIZED') { token=null; $('connection').hidden=false; $('save').disabled=true; $('unlock').disabled=true; }
}
async function busy(button, action) {
  button.disabled = true;
  try { await action(); } catch (e) { error(e); }
  finally { button.disabled = !token && ['save','unlock'].includes(button.id); }
}
function renderPalette() {
  $('palette').replaceChildren();
  if (!palette.colors.some(c=>c.hex===selected)) selected=palette.colors[0].hex;
  for (const color of palette.colors) {
    const name = names[color.id] ?? color.name ?? color.hex;
    const button=document.createElement('button'); button.type='button'; button.className='swatch';
    button.style.backgroundColor=color.hex; button.setAttribute('aria-label',name); button.title=name;
    button.setAttribute('aria-pressed',String(color.hex===selected));
    button.onclick=()=>{selected=color.hex;renderPalette();}; $('palette').append(button);
  }
  $('tickets').textContent=`컬러티켓 ${palette.tickets}장`;
  $('selected').textContent=`선택한 색: ${names[palette.colors.find(c=>c.hex===selected)?.id] ?? selected}`;
}
function applyArtwork(art) {
  revision=art.revision;
  strokes=art.strokes??[];legacyRegions=art.legacyRegions??art.regions??{};undone=[];active=null;redraw();
  stickerEditor.setPlacements(art.stickers??[]);
  dirty=false; $('save-state').textContent='저장된 그림을 불러왔어요.';
}
async function load() {
  const [colors,books,catalog] = await Promise.all([api('/api/session/palette'),api('/api/session/library'),api('/api/session/catalog')]);
  renderCatalog(catalog);
  palette=colors;library=books;renderPalette();renderLibrary();
  const remembered=localStorage.getItem('pj-last-page-'+library.familyId);
  const page=library.pages.find(p=>p.id===currentPage?.id)||library.pages.find(p=>p.id===remembered)||library.pages[0];
  if(page)await openPage(page.id);else{currentPage=null;strokes=[];legacyRegions={};redraw();}
  $('connection').hidden=true; $('studio').hidden=false; $('save').disabled=false; $('unlock').disabled=false;
}
function renderCatalog(catalog){
  stickerEditor.setCatalog(catalog.stickers);$('sticker-picker').replaceChildren();
  for(const item of catalog.stickers){const button=document.createElement('button');button.className='secondary';button.disabled=!item.unlocked;
    const image=document.createElement('img');image.src=item.asset;image.alt='';const label=document.createElement('span');label.textContent=item.name+(item.unlocked?' 붙이기':' · 잠김');button.append(image,label);
    button.onclick=()=>{stickerEditor.add(item.id);setTool('move');};$('sticker-picker').append(button);}
  $('catalog-note').textContent=catalog.note;
  for(const id of ['basic-stickers','regional-stickers','regional-colors'])$(id).replaceChildren();
  for(const item of [...catalog.stickers,...catalog.colors]){
    const card=document.createElement('article');card.className='catalog-item';
    if(item.asset){const img=document.createElement('img');img.src=item.asset;img.alt=item.name;img.width=100;img.height=100;card.append(img);}
    else{const swatch=document.createElement('div');swatch.className='catalog-color';swatch.style.backgroundColor=item.hex;card.append(swatch);}
    const title=document.createElement('h3');title.textContent=item.name;
    const description=document.createElement('p');description.textContent=item.description;
    const state=document.createElement('p');state.className='hint';state.textContent=item.kind==='basic'?'처음부터 제공 · 직접 색칠하는 스티커':item.unlocked?'해금됨':`잠김 · ${item.acquisition}`;
    card.append(title,description,state);$(item.asset?(item.kind==='basic'?'basic-stickers':'regional-stickers'):'regional-colors').append(card);
  }
}
function options(id,rows,value){$(id).replaceChildren(...rows.map(row=>{const option=document.createElement('option');option.value=row.id;option.textContent=row.title??row.name;return option;}));if(rows.some(r=>r.id===value))$(id).value=value;}
function renderLibrary(){
  const child=$('child').value,trip=$('trip').value,diary=$('diary').value,page=$('page').value;
  options('child',library.children,child);options('trip',library.trips,trip);
  options('diary',library.diaries.filter(d=>d.childId===$('child').value&&d.tripId===$('trip').value),diary);
  options('page',library.pages.filter(p=>p.diaryId===$('diary').value),page);
}
async function openPage(id){
  const page=await api('/api/session/pages/'+id);currentPage=page;autoBlocked=false;clearTimeout(saveTimer);
  const diary=library.diaries.find(d=>d.id===page.diaryId);
  $('child').value=diary.childId;$('trip').value=diary.tripId;renderLibrary();$('diary').value=diary.id;renderLibrary();$('page').value=page.id;
  $('page-title').value=page.title;$('page-text').value=page.text;
  $('page-title').disabled=false;$('page-text').disabled=false;$('save').disabled=false;
  document.querySelector('.drawing-surface').classList.toggle('blank',page.template==='blank');applyArtwork(page);
  localStorage.setItem('pj-last-page-'+library.familyId,page.id);
  $('recover-draft').hidden=true;const draft=localStorage.getItem('pj-draft-'+page.id);
  if(draft){try{const data=JSON.parse(draft);if(data.revision===page.revision){$('page-title').value=data.title;$('page-text').value=data.text;applyArtwork(data);changed();status('이 기기의 임시 기록을 복원했어요.');}else{$('recover-draft').hidden=false;status('서버와 임시 기록의 버전이 달라요. 서버 기록을 불러왔으며 임시 기록도 보존했어요.');}}catch{status('임시 기록을 읽지 못해 서버 기록을 불러왔어요.');}}
}
$('recover-draft').onclick=()=>{
  if(!currentPage||!confirm('이 기기의 임시 기록으로 이어쓰면 다음 저장 때 서버 내용을 바꿉니다. 계속할까요?'))return;
  try{const data=JSON.parse(localStorage.getItem('pj-draft-'+currentPage.id));
    $('page-title').value=data.title;$('page-text').value=data.text;
    applyArtwork({...data,revision:currentPage.revision,legacyRegions:currentPage.legacyRegions});autoBlocked=false;changed();$('recover-draft').hidden=true;
  }catch{status('임시 기록을 읽지 못했어요.');}
};
async function saveCurrent(){
  clearTimeout(saveTimer);if(saving){await saving;return;}if(!token||!currentPage||!dirty||active||stickerEditor.isActive())return;
  const pageId=currentPage.id,version=editVersion,data=snapshot();
  saving=(async()=>{try{
    const saved=await api('/api/session/pages/'+pageId,'PUT',data);
    if(currentPage?.id!==pageId)return;
    revision=saved.revision;currentPage=saved;dirty=editVersion!==version;
    const item=library.pages.find(p=>p.id===pageId);if(item)item.title=saved.title;
    if(!dirty){localStorage.removeItem('pj-draft-'+pageId);$('save-state').textContent='서버에 저장했어요.';renderLibrary();}
    else{localStorage.setItem('pj-draft-'+pageId,JSON.stringify(snapshot()));clearTimeout(saveTimer);saveTimer=setTimeout(()=>busy($('save'),saveCurrent),1200);}
  }catch(e){if(e.message==='ARTWORK_CONFLICT')autoBlocked=true;$('save-state').textContent='서버 저장에 실패했어요. 이 기기의 임시 기록은 유지됩니다.';throw e;}finally{saving=null;}})();
  await saving;
}
async function leavePage(){if(active||stickerEditor.isActive())return false;await saveCurrent();return !dirty;}
function restoreSelection(){if(!currentPage)return;const d=library.diaries.find(item=>item.id===currentPage.diaryId);$('child').value=d.childId;$('trip').value=d.tripId;renderLibrary();$('diary').value=d.id;renderLibrary();$('page').value=currentPage.id;}
for(const id of ['child','trip','diary','page'])$(id).onchange=()=>busy($(id),async()=>{
  const wanted=$(id).value;
  try {if(!await leavePage()){restoreSelection();status('현재 페이지의 저장이 끝난 뒤 이동해주세요.');return;}
  if(id==='page'){await openPage(wanted);return;}renderLibrary();const next=$('page').value;
  if(next)await openPage(next);else{clearPage();status('그림일기와 페이지를 추가해주세요.');}
  }catch(e){restoreSelection();throw e;}
});
function clearPage(){clearTimeout(saveTimer);currentPage=null;strokes=[];legacyRegions={};stickerEditor.setPlacements([]);undone=[];dirty=false;$('page-title').value='';$('page-text').value='';$('page-title').disabled=true;$('page-text').disabled=true;document.querySelector('.drawing-surface').classList.add('blank');redraw();}
for(const kind of ['child','trip','diary'])$(kind+'-form').onsubmit=event=>{event.preventDefault();busy(event.submitter,async()=>{
  if(!await leavePage())return;
  const path={child:'children',trip:'trips',diary:'diaries'}[kind];
  const body=kind==='child'?{name:$('child-name').value}:{title:$(kind+'-title').value,...(kind==='diary'?{childId:$('child').value,tripId:$('trip').value}:{})};
  const added=await api('/api/session/'+path,'POST',body);library=await api('/api/session/library');renderLibrary();$(kind).value=added.id;renderLibrary();
  clearPage();status('추가했어요. 여행과 그림일기를 선택하고 페이지를 만들어주세요.');event.target.reset();
});};
$('page-form').onsubmit=event=>{event.preventDefault();busy(event.submitter,async()=>{
  if(!await leavePage())return;if(!$('diary').value){status('먼저 그림일기를 추가해주세요.');return;}
  const page=await api('/api/session/diaries/'+$('diary').value+'/pages','POST',{title:$('new-page-title').value,template:$('new-template').value});
  library=await api('/api/session/library');await openPage(page.id);event.target.reset();status('새 페이지를 만들었어요.');
});};
$('page-title').oninput=changed;$('page-text').oninput=changed;
$('join-form').onsubmit = event => {
  event.preventDefault();
  busy($('join'), async()=>{
    if (dirty && !confirm('연결을 바꾸면 저장하지 않은 그림이 사라집니다. 계속할까요?')) return;
    const code=$('code').value.trim().toLowerCase();
    if (!/^[a-f0-9]{24}$/.test(code)) {status('24자리 연결 코드를 확인해주세요.');return;}
    if (pendingJoin?.code!==code) pendingJoin={code, token:randomToken()};
    const candidate=pendingJoin.token;
    await api('/api/sessions/join','POST',{code},candidate);
    // Save before loading: a network retry must retain the already joined credential.
    localStorage.setItem(storageKey,candidate); token=candidate;
    await load(); pendingJoin=null; $('code').value=''; status('여행에 연결됐어요. 색칠을 시작해보세요.');
  });
};
function point(event){const rect=canvas.getBoundingClientRect();return [Math.round(Math.max(0,Math.min(600,(event.clientX-rect.left)*600/rect.width))*10)/10,Math.round(Math.max(0,Math.min(400,(event.clientY-rect.top)*400/rect.height))*10)/10];}
canvas.onpointerdown=event=>{
  if(!token||!palette||!currentPage||active||stickerEditor.isActive()||tool==='move'||event.button!==0)return;
  stickerEditor.select(null);
  if(strokes.length>=1000||strokes.reduce((sum,s)=>sum+s.points.length,0)+stickerEditor.pointCount()>=20000){status('그림의 획 한도에 도달했어요. 실행 취소 후 다시 그려주세요.');return;}
  active={tool,color:selected,width:Number($('size').value),points:[point(event)],pointerId:event.pointerId};
  canvas.setPointerCapture(event.pointerId);redraw();
};
canvas.onpointermove=event=>{
  if(!active||active.pointerId!==event.pointerId)return;
  const points=active.points,next=point(event),last=points.at(-1);
  if(Math.hypot(next[0]-last[0],next[1]-last[1])<1)return;
  if(points.length<2000&&strokes.reduce((sum,s)=>sum+s.points.length,0)+stickerEditor.pointCount()+points.length<20000){points.push(next);redraw();}
};
function finish(event){if(!active||active.pointerId!==event.pointerId)return;const {pointerId,...stroke}=active;strokes.push(stroke);active=null;undone=[];changed();redraw();}
canvas.onpointerup=finish;canvas.onpointercancel=finish;canvas.onlostpointercapture=finish;
function setTool(value){tool=value;stickerEditor.mode(value);$('brush').setAttribute('aria-pressed',String(value==='paint'));$('eraser').setAttribute('aria-pressed',String(value==='erase'));$('move-sticker').setAttribute('aria-pressed',String(value==='move'));}
$('brush').onclick=()=>setTool('paint');$('eraser').onclick=()=>setTool('erase');$('move-sticker').onclick=()=>setTool('move');
$('sticker-width').oninput=()=>stickerEditor.resize(Number($('sticker-width').value),Number($('sticker-height').value));
$('sticker-height').oninput=$('sticker-width').oninput;$('remove-sticker').onclick=()=>stickerEditor.remove();
$('size').oninput=()=>{$('size-label').textContent=$('size').value;};
$('undo').onclick=()=>{if(stickerEditor.undo())return;if(strokes.length){undone.push(strokes.pop());changed();redraw();}};
$('redo').onclick=()=>{if(stickerEditor.redo())return;if(undone.length){strokes.push(undone.pop());changed();redraw();}};
$('save').onclick=()=>busy($('save'),async()=>{
  if(!currentPage){status('먼저 페이지를 추가해주세요.');return;}
  if(active){status('펜을 뗀 뒤 저장해주세요.');return;}
  await saveCurrent();status('페이지를 저장했어요.');
});
$('reload').onclick=()=>busy($('reload'),async()=>{
  if(dirty&&!confirm('저장하지 않은 변경을 버리고 저장된 그림을 불러올까요?'))return;
  clearTimeout(saveTimer);if(currentPage)localStorage.removeItem('pj-draft-'+currentPage.id);dirty=false;
  await load();status('저장된 그림을 불러왔어요.');
});
$('unlock-form').onsubmit=event=>{event.preventDefault();busy($('unlock'),async()=>{
  palette=await api('/api/session/palette/personal','POST',{hex:$('personal').value});selected=$('personal').value.toUpperCase();renderPalette();status('새 색이 팔레트에 추가됐어요.');
});};
$('disconnect').onclick=()=>{
  if(dirty&&!confirm('저장하지 않은 그림이 있어요. 연결에서 나갈까요?'))return;
  clearTimeout(saveTimer);localStorage.removeItem(storageKey);token=null;palette=null;dirty=false;pendingJoin=null;currentPage=null;
  $('studio').hidden=true;$('connection').hidden=false;status('이 기기에서 연결을 나왔어요.');
};
window.addEventListener('beforeunload',event=>{if(dirty){event.preventDefault();event.returnValue='';}});
window.addEventListener('online',()=>{if(token&&dirty&&!autoBlocked)busy($('save'),saveCurrent);});
try { token=localStorage.getItem(storageKey); if(token) await load(); }
catch(e) { error(e); }
