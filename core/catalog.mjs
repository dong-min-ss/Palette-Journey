import stickerData from '../web/assets/stickers/catalog.json' with {type:'json'};
import {BASE_COLORS} from './palette.mjs';
export const CATALOG_GROUPS=Object.freeze([
 {id:'basic',name:'기본'},{id:'seoul',name:'서울'},{id:'gyeonggi',name:'경기도'},
 {id:'gangwon',name:'강원도'},{id:'chungcheong',name:'충청도'},{id:'jeolla',name:'전라도'},
 {id:'gyeongsang',name:'경상도'},{id:'jeju',name:'제주도'},
]);
// Curated tourist collections, not administrative boundaries or measured standard colors.
export const CATALOG_CITIES=Object.freeze([
 {id:'gyeongju',name:'경주',groupId:'gyeongsang',latitude:35.8347,longitude:129.219},
 {id:'seoul',name:'서울',groupId:'seoul',latitude:37.5796,longitude:126.977},
 {id:'busan',name:'부산',groupId:'gyeongsang',latitude:35.1532,longitude:129.1186},
 {id:'jeju',name:'제주',groupId:'jeju',latitude:33.5117,longitude:126.5219},
 {id:'gangneung',name:'강릉',groupId:'gangwon',latitude:37.7736,longitude:128.948},
]);
export const STICKERS=Object.freeze(stickerData.map(s=>Object.freeze({...s})));
export const REGIONAL_COLORS=Object.freeze([
 ['gyeongju-stone','첨성대 돌빛','#C4B69B','gyeongju','첨성대'],
 ['gyeongju-gold','신라 금빛','#D9AA38','gyeongju','신라 금관'],
 ['gyeongju-night','첨성대 별밤','#293C70','gyeongju','별을 바라보는 밤'],
 ['seoul-dancheong','궁궐 단청','#BD4B40','seoul','궁궐 단청'],
 ['seoul-river','한강 물빛','#6A9FBD','seoul','한강'],
 ['seoul-sunset','서울 노을','#DFAC7C','seoul','도시의 저녁'],
 ['busan-sea','해운대 바다','#238DAD','busan','해운대'],
 ['busan-night','광안리 밤빛','#334D89','busan','광안대교'],
 ['busan-camellia','동백 꽃빛','#D85D78','busan','동백꽃'],
 ['jeju-tangerine','감귤 주황','#F2A029','jeju','감귤'],
 ['jeju-basalt','현무암 돌빛','#444A4C','jeju','현무암'],
 ['jeju-sea','제주 바다','#36B8B0','jeju','제주의 바다'],
 ['gangneung-pine','경포 솔빛','#47765A','gangneung','소나무'],
 ['gangneung-coffee','강릉 커피빛','#78523B','gangneung','커피 거리'],
 ['gangneung-dawn','동해 새벽','#E7A3A1','gangneung','동해의 새벽'],
].map(([id,name,hex,regionId,motif])=>Object.freeze({id,name,hex,regionId,motif,description:`${motif}에서 영감을 얻은 창작용 색이에요.`,acquisition:'모의 방문 구역에서 120초 체류'})));
const baseNames=['빨강','주황','노랑','초록','파랑','보라','핑크','브라운','화이트','블랙','그레이'];
export function contentCatalog(palette,owned=[],development=false){
 const stickers=STICKERS.map(item=>{
  const record=owned.find(s=>s.stickerId===item.id),acquired=item.kind==='basic'||!!record?.visitId;
  return {...item,acquired,developmentUnlocked:development&&item.kind==='regional'&&!acquired,rewardSource:record?.visitId?.startsWith('simulation-')?'simulation':null,unlocked:acquired||!!record};
 });
 const colors=REGIONAL_COLORS.map(item=>{const record=palette.colors.find(c=>c.id===`region-${item.id}`);return {...item,acquired:!!record,rewardSource:record?.visitSource??null,unlocked:!!record};});
 const count=items=>({acquired:items.filter(i=>i.acquired).length,total:items.length});
 const cities=CATALOG_CITIES.map(city=>({...city,colors:count(colors.filter(i=>i.regionId===city.id)),stickers:count(stickers.filter(i=>i.regionId===city.id))}));
 const basicColors=BASE_COLORS.map((c,i)=>({...c,name:baseNames[i],kind:'basic',acquired:true,unlocked:true}));
 const personalColors=palette.colors.filter(c=>c.source==='personal').map(c=>({...c,name:'나만의 색',acquired:true,unlocked:true}));
 return {region:{id:'gyeongju',name:'경주'},phase:'simulation',groups:CATALOG_GROUPS,cities,basicColors,personalColors,colors,stickers,
  note:(development?'개발용 접근은 수집 개수에 포함하지 않아요. ':'')+'5개 도시의 모의 방문 컬렉션이에요. 실제 방문 인증·행정구역 경계·표준색과는 달라요.'};
}
