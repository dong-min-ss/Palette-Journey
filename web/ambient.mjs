export function createAmbientSync({api,getToken}){
  const button=document.getElementById('ambient-toggle'),status=document.getElementById('ambient-status');
  let enabled=false,colorId=null,busy=false,version=0;
  const labels={applied:'차량에서 같은 색 적용·재조회를 확인했어요.',blocked:'차량 정차 신호를 확인하면 적용해요.',unsupported:'이 차량에서는 조명 제어를 지원하지 않아요.',error:'조명 적용을 확인하지 못했어요. 차량 화면을 확인해주세요.',waiting:'차량의 색 조명 연동 화면에서 연결을 기다려요.',off:'조명 연동이 꺼져 있어요.'};
  function render(){button.disabled=!getToken();button.setAttribute('aria-pressed',String(enabled));button.textContent=enabled?'색 조명 연동 끄기':'색 조명 연동 켜기';}
  async function sync(){
    if(!getToken()||busy||!colorId)return;
    const epoch=version,credential=getToken();busy=true;
    try{const result=await api('/api/session/ambient','PUT',{enabled,colorId});if(epoch===version&&credential===getToken())status.textContent=labels[result.status]??labels.waiting;}
    catch{if(epoch===version)status.textContent='차량 연결을 확인해주세요. 연결이 끊기면 조명 적용은 멈춰요.';}
    finally{busy=false;if(epoch!==version&&getToken())void sync();}
  }
  button.onclick=()=>{enabled=!enabled;version++;render();void sync();};
  setInterval(()=>{render();if(enabled&&!document.hidden)void sync();},2000);
  document.addEventListener('visibilitychange',()=>{if(document.hidden&&enabled){enabled=false;version++;render();void sync();}});
  document.addEventListener('palette-app-background',()=>{if(enabled){enabled=false;version++;render();void sync();}});
  window.addEventListener('pagehide',()=>{enabled=false;version++;void sync();});
  render();
  return {select(id){if(colorId!==id)version++;colorId=id;render();if(enabled)void sync();},reset(){enabled=false;version++;render();status.textContent=labels.off;void sync();}};
}
