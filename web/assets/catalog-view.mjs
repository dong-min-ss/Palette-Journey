// Shared read-only collection navigation for the web and the offline Android renderer.
export function mountCatalog({root,catalog,assetUrl=item=>item.asset,initialSelection={}}){
 let data=catalog,selection={groupId:null,cityId:null,ownedOnly:false,...initialSelection};
 const el=(tag,text,className)=>{const node=document.createElement(tag);if(text!==undefined)node.textContent=text;if(className)node.className=className;return node;};
 const acquired=item=>item.acquired===true;
 const total=items=>items.filter(acquired).length+'/'+items.length;
 const button=(label,action,className)=>{const node=el('button',label,className);node.type='button';node.onclick=action;return node;};
 const navigate=(groupId=null,cityId=null)=>{selection={groupId,cityId,ownedOnly:false};render();root.scrollIntoView?.({block:'start'});};
 const groupItems=ids=>({colors:data.colors.filter(i=>ids.includes(i.regionId)),stickers:data.stickers.filter(i=>ids.includes(i.regionId))});
 function tile(title,subtitle,action,attribute,id,disabled=false){const node=button('',action,'collection-tile');node.setAttribute(attribute,id);node.disabled=disabled;node.append(el('strong',title),el('small',subtitle));return node;}
 function cards(title,items){
  root.append(el('h3',title,'collection-section-title'));const grid=el('div',undefined,'collection-grid');
  for(const item of items.filter(i=>!selection.ownedOnly||acquired(i))){
   const card=el('article',undefined,'collection-card');card.dataset.contentId=item.id;
   if(item.asset){const image=el('img');image.src=assetUrl(item);image.alt=item.name;card.append(image);}else{const swatch=el('div',undefined,'collection-swatch');swatch.style.backgroundColor=item.hex;swatch.setAttribute('aria-label',item.name);card.append(swatch);}
   const state=item.kind==='basic'?'기본 제공':item.developmentUnlocked?'개발용':acquired(item)?item.rewardSource==='simulation'?'보유 · 모의 보상':'보유':'미획득';
   card.append(el('h4',item.name),el('span',state,'collection-badge '+(acquired(item)?'is-owned':'is-locked')));
   const detail=el('details'),summary=el('summary','자세히');detail.append(summary);
   if(item.description)detail.append(el('p',item.description));if(item.acquisition)detail.append(el('p',item.acquisition));
   if(item.developmentUnlocked)detail.append(el('p','개발용 접근 · 수집한 항목은 아니에요.'));
   if(item.rewardSource==='simulation')detail.append(el('p','모의 방문 보상 · 실제 방문 인증 아님'));
   if(item.hex)detail.append(el('p',item.hex));card.append(detail);grid.append(card);
  }
  if(!grid.childElementCount)grid.append(el('p','아직 보유한 항목이 없어요.','collection-empty'));root.append(grid);
 }
 function render(){
  root.replaceChildren();root.classList.add('collection');
  const group=data.groups.find(g=>g.id===selection.groupId),city=data.cities.find(c=>c.id===selection.cityId&&c.groupId===selection.groupId);
  if(!group)selection={groupId:null,cityId:null,ownedOnly:false};else if(selection.cityId&&!city)selection.cityId=null;
  if(!group){
   root.append(el('h2','전국 컬렉션'));const colors=[...data.basicColors,...data.personalColors,...data.colors],stickers=data.stickers;
   root.append(el('p',`내 색 ${colors.filter(acquired).length} · 스티커 ${stickers.filter(acquired).length}`,'collection-summary'));
   const grid=el('div',undefined,'collection-grid');
   for(const g of data.groups){const cities=data.cities.filter(c=>c.groupId===g.id),items=groupItems(cities.map(c=>c.id));const subtitle=g.id==='basic'?`색 ${data.basicColors.length} · 스티커 ${data.stickers.filter(s=>s.kind==='basic').length}`:!cities.length?'준비 중':(cities.map(c=>c.name).filter(n=>n!==g.name).join(' · ')+`\n색 ${total(items.colors)} · 스티커 ${total(items.stickers)}`).trim();grid.append(tile(g.name,subtitle,()=>navigate(g.id),'data-collection-group',g.id,g.id!=='basic'&&!cities.length));}
   root.append(grid,el('p','모의 방문 컬렉션 · 실제 방문 인증 아님','collection-note'));return;
  }
  const trail=el('div',undefined,'collection-trail');trail.append(button('전체',()=>navigate()));if(city)trail.append(button(group.name,()=>navigate(group.id)));trail.append(el('span',city?.name??group.name));root.append(trail);
  if(group.id!=='basic'&&!city){
   root.append(el('h2',group.name));const grid=el('div',undefined,'collection-grid');for(const c of data.cities.filter(c=>c.groupId===group.id)){const items=groupItems([c.id]);grid.append(tile(c.name,`색 ${total(items.colors)} · 스티커 ${total(items.stickers)}`,()=>navigate(group.id,c.id),'data-collection-city',c.id));}root.append(grid);return;
  }
  const colors=city?data.colors.filter(i=>i.regionId===city.id):data.basicColors,stickers=city?data.stickers.filter(i=>i.regionId===city.id):data.stickers.filter(i=>i.kind==='basic');
  root.append(el('h2',city?city.name+' 컬렉션':'기본 컬렉션'),el('p',`색 ${total(colors)} · 스티커 ${total(stickers)}`,'collection-summary'));
  const filter=button(selection.ownedOnly?'전체 보기':'보유한 것만',()=>{selection.ownedOnly=!selection.ownedOnly;render();},'collection-filter');filter.setAttribute('aria-pressed',String(selection.ownedOnly));root.append(filter);
  if(city)root.append(el('p','시연 구역에서 120초 체류 · 실제 방문 인증 아님','collection-note'));
  cards('색깔',colors);cards('스티커',stickers);if(!city&&data.personalColors.length)cards('나만의 색',data.personalColors);
 }
 render();return {update(next){data=next;render();},getSelection(){return {...selection};}};
}
