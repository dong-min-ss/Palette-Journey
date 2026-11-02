import {createConversationPlayback} from './conversation-playback.mjs';
export const QUESTIONS=['오늘 어떤 일이 있었어?','어떤 게 가장 재미있었어?','뭐가 제일 맛있었어?','다음에는 누구와 무엇을 하고 싶어?'];

export function createVoiceDiary({getPage,getToken,api}) {
  const $=id=>document.getElementById(id);
  let page=null,metadata={revision:0,recording:null},recorder=null,stream=null,pending=false,working=false,draft=null,url=null;
  let question=0,cues=[],timer=null,elapsed=0,segment=0,generation=0,loading=false;
  const active=()=>pending||recorder?.state==='recording'||recorder?.state==='paused';
  const duration=()=>Math.round(elapsed+(recorder?.state==='recording'?performance.now()-segment:0));
  const say=text=>$('voice-status').textContent=text;
  const conversation=createConversationPlayback({onStatus:say,onChange:()=>render()});
  function releaseURL(){conversation.stop(); $('voice-player').pause();$('voice-player').removeAttribute('src');$('voice-player').load();if(url)URL.revokeObjectURL(url);url=null;}
  function preview(blob){releaseURL();url=URL.createObjectURL(blob);$('voice-player').src=url;$('voice-player').hidden=false;}
  function cleanup(){clearInterval(timer);timer=null;stream?.getTracks().forEach(t=>t.stop());stream=null;}
  function render(){
    const listening=conversation.active();const recording=active();$('voice-start').disabled=!page||recording||working||loading||!!draft||listening;
    $('voice-stop').disabled=!recording;
    $('voice-pause').disabled=!recorder||recorder.state==='inactive'||working;
    $('voice-pause').textContent=recorder?.state==='paused'?'녹음 이어가기':'잠시 쉬기';
    $('voice-next').disabled=!recorder||recorder.state!=='recording'||question>=QUESTIONS.length-1;
    $('voice-download').disabled=!draft||working;$('voice-save').disabled=!draft||working;$('voice-discard').disabled=!draft||working;
    $('voice-play').disabled=!metadata.recording||recording||working||!!draft||loading||listening;
    $('voice-answer-only').hidden=metadata.recording?.mode!=='vehicle-answer-original';$('voice-answer-only').disabled=$('voice-play').disabled;
    $('voice-play').textContent=metadata.recording?.mode==='vehicle-answer-original'?'질문과 답변 함께 듣기':'저장된 대화 듣기';
    $('voice-play-stop').disabled=!listening&&$('voice-player').paused!==false;
    $('voice-delete').disabled=!metadata.recording||recording||working||!!draft||loading||listening;
    $('voice-question').textContent=QUESTIONS[question];
    $('voice-indicator').hidden=!recording;$('voice-indicator').textContent=pending?'마이크 연결 중':recorder?.state==='paused'?'녹음 일시정지':'● 녹음 중';
    $('voice-clock').textContent=`${Math.floor(duration()/1000)} / 120초`;
  }
  function showCues(value){$('voice-cues').replaceChildren(...value.map(c=>{const li=document.createElement('li');li.textContent=`${Math.floor(c.at/1000)}초 · ${c.questionText??QUESTIONS[c.question]}${c.questionText?(c.skipped?" · 건너뜀":" · 답변: "+c.answerText):""}`;return li;}));}
  async function load(id){
    const epoch=++generation;page=id;draft=null;metadata={revision:0,recording:null};question=0;elapsed=0;releaseURL();$('voice-player').hidden=true;showCues([]);loading=!!id;$('voice-consent').checked=false;render();
    if(!id){say('페이지를 선택해주세요.');return;}
    say('음성 기록을 확인하고 있어요.');
    try {const result=await api(`/api/session/pages/${id}/voice`);if(epoch!==generation)return;metadata=result;showCues(result.recording?.cues??[]);say(result.recording?(result.recording.mode==='vehicle-answer-original'?'질문은 이 기기의 한국어 음성으로 다시 읽고 답변은 차량 녹음 원음으로 들려줘요. 차량 TTS 목소리와는 다를 수 있어요.':'저장된 대화를 다시 들을 수 있어요.'):'아직 음성 기록이 없어요. 보호자와 함께 시작해요.');}
    catch(error){if(epoch!==generation)return;say('음성 기록을 불러오지 못했어요. 메뉴를 다시 열어 재시도해주세요.');}
    finally{if(epoch===generation){loading=false;render();}}
  }
  async function start(){
    if(!page||active()||draft||working||loading)return;
    if(!navigator.mediaDevices?.getUserMedia||!globalThis.MediaRecorder){say('이 브라우저에서는 녹음을 지원하지 않아요. Chrome 또는 Safari의 HTTPS/로컬 환경에서 열어주세요.');return;}
    if(!$('voice-consent').checked){say('보호자가 녹음 안내를 확인하고 동의해주세요.');$('voice-consent').focus();return;}
    if(metadata.recording&&!confirm('새로 녹음할까요? 기존 녹음은 새 녹음을 저장하기 전까지 유지됩니다.'))return;
    pending=true;render();say('마이크 사용을 허용해주세요.');const epoch=generation;
    try{
      const acquired=await navigator.mediaDevices.getUserMedia({audio:true});
      if(epoch!==generation||!page){acquired.getTracks().forEach(t=>t.stop());return;}
      stream=acquired;
      const mime=['audio/webm;codecs=opus','audio/ogg;codecs=opus','audio/mp4'].find(type=>MediaRecorder.isTypeSupported(type));
      if(!mime)throw new Error('UNSUPPORTED');
      recorder=new MediaRecorder(stream,{mimeType:mime,audioBitsPerSecond:64000});
      const chunks=[];let size=0,failed=false;const recordingPage=page;
      recorder.ondataavailable=event=>{if(event.data.size){size+=event.data.size;chunks.push(event.data);if(size>2097152&&recorder.state!=='inactive'){failed=true;stop();}}};
      recorder.onerror=()=>{failed=true;stop();say('녹음 장치 오류가 발생했어요. 마이크 연결을 확인해주세요.');};
      recorder.onstop=()=>{
        cleanup();working=false;if(epoch!==generation)return;
        const ms=Math.max(1,Math.min(120000,elapsed));
        const blob=new Blob(chunks,{type:mime.split(';')[0]});
        if(!failed&&blob.size>=12&&blob.size<=2097152){draft={blob,duration:ms,cues:structuredClone(cues),page:recordingPage};preview(blob);showCues(cues);say('녹음이 끝났어요. 들어본 뒤 저장해주세요. 저장 전에는 이 화면에만 있어요.');}
        else say('녹음 파일을 만들지 못했어요. 다시 녹음해주세요.');
        recorder=null;render();
      };
      question=0;elapsed=0;cues=[{question:0,at:0}];pending=false;segment=performance.now();recorder.start(1000);
      timer=setInterval(()=>{if(duration()>=120000)stop();render();},250);
      say('녹음 중이에요. 보호자가 질문을 읽고, 아이의 답을 기다려주세요.');render();if(document.hidden)pause();
    }catch(error){if(epoch!==generation)return;cleanup();pending=false;recorder=null;say(error.name==='NotAllowedError'?'마이크 권한이 없어 녹음을 시작하지 않았어요. 브라우저 권한을 확인해주세요.':'녹음을 시작하지 못했어요. 마이크 연결과 지원 형식을 확인해주세요.');render();}
  }
  function stop(){if(pending){generation++;pending=false;say('녹음 시작을 취소했어요.');render();return;}if(!recorder||recorder.state==='inactive')return;elapsed=duration();working=true;recorder.stop();cleanup();render();}
  function pause(){if(recorder?.state==='recording'){elapsed=duration();recorder.pause();stream?.getAudioTracks().forEach(t=>t.enabled=false);say('쉬는 중이에요. 이 동안의 대화는 녹음하지 않아요.');}else if(recorder?.state==='paused'){stream?.getAudioTracks().forEach(t=>t.enabled=true);segment=performance.now();recorder.resume();say('녹음을 이어가요.');}render();}
  $('voice-start').onclick=start;$('voice-stop').onclick=stop;$('voice-pause').onclick=pause;
  $('voice-next').onclick=()=>{if(recorder?.state!=='recording'||question>=3)return;question++;cues.push({question,at:Math.min(120000,duration())});showCues(cues);render();};
  $('voice-save').onclick=async()=>{
    if(!draft||working)return;working=true;render();
    try{
      const value=draft,buffer=new Uint8Array(await value.blob.arrayBuffer());let binary='';for(const b of buffer)binary+=String.fromCharCode(b);
      metadata=await api(`/api/session/pages/${value.page}/voice`,'PUT',{revision:metadata.revision,mime:value.blob.type,audio:btoa(binary),duration:value.duration,cues:value.cues});
      draft=null;say('가족 비공개 음성 일기로 저장했어요.');
    }catch(error){say(error.message==='VOICE_CONFLICT'?'다른 기기에서 음성 기록이 바뀌었어요. 현재 녹음은 이 화면에 남겨뒀어요. 기기에 내려받은 뒤 저장 기록을 다시 확인해주세요.':'저장에 실패했어요. 녹음은 이 화면에 남아 있어요. 연결 후 다시 저장해주세요.');}
    finally{working=false;render();}
  };
  $('voice-download').onclick=()=>{if(!draft)return;const a=document.createElement('a');a.href=url;a.download='여행-음성일기.'+({ 'audio/webm':'webm','audio/ogg':'ogg','audio/mp4':'m4a'}[draft.blob.type]);a.click();};
  $('voice-discard').onclick=()=>{if(!draft||!confirm('저장 전 녹음을 버릴까요?'))return;load(page);};
  async function playRecording(withQuestions){
    if(!metadata.recording||working||conversation.active())return;
    if(withQuestions&&metadata.recording.mode==='vehicle-answer-original'){
      const id=page,credential=getToken(),revision=metadata.revision;releaseURL();$('voice-player').hidden=true;
      conversation.start(structuredClone(metadata.recording),async()=>{const response=await fetch(`/api/session/pages/${id}/voice/audio`,{headers:{Authorization:`Bearer ${credential}`}});if(!response.ok||response.headers.get('X-Voice-Revision')!==String(revision))throw new Error();return response.arrayBuffer();});render();return;
    }
    working=true;render();const epoch=generation;
    try{const response=await fetch(`/api/session/pages/${page}/voice/audio`,{headers:{Authorization:`Bearer ${getToken()}`}});if(!response.ok)throw new Error();const blob=await response.blob();if(epoch!==generation)return;preview(blob);await $('voice-player').play();say(metadata.recording.mode==='vehicle-answer-original'?'차량에서 녹음한 답변 원음을 이어서 재생해요. 질문 음성은 포함하지 않아요.':'저장된 대화를 재생해요.');}
    catch{say('재생하지 못했어요. 연결과 재생 버튼을 확인해주세요.');}finally{working=false;render();}
  };
  $('voice-play').onclick=()=>playRecording(true);
  $('voice-answer-only').onclick=()=>playRecording(false);
  $('voice-play-stop').onclick=()=>{releaseURL();$('voice-player').hidden=true;say('재생을 중지했어요.');render();};
  $('voice-player').onplay=render;$('voice-player').onpause=render;$('voice-player').onended=render;
  $('voice-delete').onclick=async()=>{
    if(!metadata.recording||working||!confirm('이 페이지의 저장된 음성 기록을 삭제할까요?'))return;working=true;render();
    try{metadata=await api(`/api/session/pages/${page}/voice`,'DELETE',{revision:metadata.revision});releaseURL();$('voice-player').hidden=true;showCues([]);say('저장된 음성 기록을 삭제했어요.');}
    catch(error){say(error.message==='VOICE_CONFLICT'?'다른 기기에서 기록이 바뀌었어요. 메뉴를 닫고 다시 열어 확인해주세요.':'삭제하지 못했어요. 연결 후 다시 시도해주세요.');}finally{working=false;render();}
  };
  document.addEventListener('visibilitychange',()=>{if(document.hidden){if(recorder?.state==='recording')pause();if(conversation.active()){conversation.stop();say('화면을 벗어나 질문·답변 재생을 중지했어요.');}}});
  window.addEventListener('pagehide',()=>{generation++;stop();cleanup();releaseURL();});
  return {blocked:()=>!!(active()||draft||working),load,
    opened(){if(getPage()?.id!==page||(!active()&&!draft&&!working))load(getPage()?.id??null);},
    reset(){generation++;pending=false;cleanup();load(null);}};
}
