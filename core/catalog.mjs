// Curated MVP scope: Gyeongju only. Colors are creative interpretations, not measured heritage colors.
export const STICKERS = Object.freeze([
  {id:'basic-star',name:'반짝 별',kind:'basic',regionId:null,asset:'/assets/stickers/star.svg',description:'반짝이는 여행의 순간을 꾸며요.'},
  {id:'basic-heart',name:'따뜻한 마음',kind:'basic',regionId:null,asset:'/assets/stickers/heart.svg',description:'함께한 가족에게 마음을 전해요.'},
  {id:'gyeongju-cheomseongdae',name:'경주 첨성대',kind:'regional',regionId:'gyeongju',asset:'/assets/stickers/cheomseongdae.svg',description:'별을 바라보던 첨성대를 떠올리며 여행을 기록해요.',acquisition:'경주 방문 확인 후 해금 · 세부 조건 준비 중'},
  {id:'gyeongju-silla-crown',name:'반짝이는 신라 금관',kind:'regional',regionId:'gyeongju',asset:'/assets/stickers/silla-crown.svg',description:'신라의 금관에서 영감을 얻은 상상 속 왕관이에요.',acquisition:'경주 방문 확인 후 해금 · 세부 조건 준비 중'},
]);
export const REGIONAL_COLORS = Object.freeze([
  {id:'gyeongju-stone',name:'첨성대 돌빛',hex:'#C4B69B',regionId:'gyeongju',motif:'첨성대',description:'오래된 돌의 따스한 느낌을 담은 베이지예요.',acquisition:'경주 지역 통과 확인 후 수집 예정'},
  {id:'gyeongju-gold',name:'신라 금빛',hex:'#D9AA38',regionId:'gyeongju',motif:'신라 금관',description:'금관의 반짝임에서 떠올린 황금빛이에요.',acquisition:'경주 체류 또는 지정 장소 방문 확인 후 수집 예정 · 조건 준비 중'},
  {id:'gyeongju-night',name:'첨성대 별밤',hex:'#293C70',regionId:'gyeongju',motif:'별을 바라보는 밤',description:'첨성대 위로 펼쳐지는 밤하늘을 상상한 남색이에요.',acquisition:'경주 체류 또는 지정 장소 방문 확인 후 수집 예정 · 조건 준비 중'},
]);
export function contentCatalog(palette, owned=[], development=false) {
  return {region:{id:'gyeongju',name:'경주'},phase:'simulation',
    stickers:STICKERS.map(item=>({...item,developmentUnlocked:development&&item.kind==='regional'&&!owned.some(s=>s.stickerId===item.id&&s.visitId),rewardSource:owned.find(s=>s.stickerId===item.id)?.visitId?.startsWith('simulation-')?'simulation':null,unlocked:item.kind==='basic'||owned.some(s=>s.stickerId===item.id)})),
    colors:REGIONAL_COLORS.map(item=>({...item,rewardSource:palette.colors.find(c=>c.id===`region-${item.id}`)?.visitSource??null,unlocked:palette.colors.some(c=>c.id===`region-${item.id}`)})),
    note:(development?'개발용 스티커 해금 중 · 방문 인증 아님. ':'')+'경주부터 시작하는 사전이에요. 모의 방문 보상은 실제 방문 인증과 구분돼요. 실제 위치 연동은 준비 중이에요. 색은 창작용 시안이며 실제 표준색이 아니에요.'};
}
