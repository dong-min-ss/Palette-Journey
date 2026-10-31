const $ = id => document.getElementById(id);
const storageKey = 'palette-journey-participant';
const names = { red:'빨강', orange:'주황', yellow:'노랑', green:'초록', blue:'파랑', purple:'보라', pink:'핑크', brown:'브라운', white:'화이트', black:'블랙', gray:'그레이' };
let token = null, palette = null, selected = null, revision = 0, dirty = false, pendingJoin = null, editVersion = 0;
const messages = { UNAUTHORIZED:'연결이 만료되거나 종료됐어요. 새 코드로 다시 연결해주세요.', INVALID_SESSION:'연결 코드가 잘못됐거나 만료됐어요.', NO_TICKET:'사용할 티켓이 없어요.', ARTWORK_CONFLICT:'다른 기기에서 그림을 저장했어요. 현재 그림은 유지됩니다. 저장된 그림을 불러온 뒤 다시 작업해주세요.', INVALID_ARTWORK:'사용할 수 없는 색이나 그림 정보예요.' };
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
  for (const region of document.querySelectorAll('[data-region]')) {
    region.dataset.color=art.regions[region.dataset.region] ?? '';
    region.style.fill=region.dataset.color || '#FFFEFA';
  }
  dirty=false; $('save-state').textContent='저장된 그림을 불러왔어요.';
}
async function load() {
  const [colors,art] = await Promise.all([api('/api/session/palette'),api('/api/session/artwork')]);
  palette=colors; renderPalette(); applyArtwork(art);
  $('connection').hidden=true; $('studio').hidden=false; $('save').disabled=false; $('unlock').disabled=false;
}
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
for (const region of document.querySelectorAll('[data-region]')) {
  const paint=()=>{if (!palette || !token) return; region.style.fill=selected; region.dataset.color=selected; editVersion++; dirty=true; $('save-state').textContent='아직 저장하지 않은 변경이 있어요.';};
  region.addEventListener('click',paint); region.addEventListener('keydown',event=>{if (event.key==='Enter'||event.key===' '){event.preventDefault();paint();}});
}
$('save').onclick=()=>busy($('save'),async()=>{
  const regions={}, savingVersion=editVersion;
  for (const region of document.querySelectorAll('[data-region]')) {
    if(region.dataset.color) regions[region.dataset.region]=region.dataset.color;
  }
  const saved=await api('/api/session/artwork','PUT',{regions,revision});
  revision=saved.revision;dirty=editVersion!==savingVersion;$('save-state').textContent=dirty?'저장 후 새로 칠한 변경이 있어요. 한 번 더 저장해주세요.':'서버에 저장했어요. 다음에도 이어 그릴 수 있어요.';status('그림을 저장했어요.');
});
$('reload').onclick=()=>busy($('reload'),async()=>{
  if(dirty&&!confirm('저장하지 않은 변경을 버리고 저장된 그림을 불러올까요?'))return;
  await load();status('저장된 그림을 불러왔어요.');
});
$('unlock-form').onsubmit=event=>{event.preventDefault();busy($('unlock'),async()=>{
  palette=await api('/api/session/palette/personal','POST',{hex:$('personal').value});selected=$('personal').value.toUpperCase();renderPalette();status('새 색이 팔레트에 추가됐어요.');
});};
$('disconnect').onclick=()=>{
  if(dirty&&!confirm('저장하지 않은 그림이 있어요. 연결에서 나갈까요?'))return;
  localStorage.removeItem(storageKey);token=null;palette=null;dirty=false;pendingJoin=null;
  $('studio').hidden=true;$('connection').hidden=false;status('이 기기에서 연결을 나왔어요.');
};
window.addEventListener('beforeunload',event=>{if(dirty){event.preventDefault();event.returnValue='';}});
try { token=localStorage.getItem(storageKey); if(token) await load(); }
catch(e) { error(e); }
