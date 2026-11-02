// Web questions are reconstructed with a local Korean voice, never the captured Pleos voice.
export function createConversationPlayback({env=globalThis,onStatus=()=>{},onChange=()=>{}}={}){
  let active=false,epoch=0,context=null,source=null,utterance=null,timer=null;
  const clear=()=>{if(timer!==null)env.clearTimeout(timer);timer=null;};
  function stop(){const wasActive=active;active=false;epoch++;clear();
    if(utterance){utterance.onend=null;utterance.onerror=null;utterance=null;}
    if(wasActive)env.speechSynthesis?.cancel();
    if(source){source.onended=null;try{source.stop();source.disconnect();}catch{}source=null;}
    if(context){const old=context;context=null;old.close().catch(()=>{});}
    if(wasActive)onChange();
  }
  function start(recording,loadAudio){
    stop();const synth=env.speechSynthesis,Context=env.AudioContext??env.webkitAudioContext;
    const voice=synth?.getVoices().find(v=>/^ko(?:-|_|$)/i.test(v.lang)&&v.localService===true);
    if(!voice||!Context||!env.SpeechSynthesisUtterance){onStatus('기기의 한국어 질문 음성을 준비하지 못했어요. 잠시 뒤 메뉴를 다시 열고 재생하거나 답변 원음만 듣기를 선택해주세요.');return;}
    const cues=recording.cues;
    if(!Array.isArray(cues)||!cues.length||cues.some(c=>typeof c.questionText!=='string')){onStatus('질문 정보를 읽지 못했어요. 답변 원음만 듣기를 선택해주세요.');return;}
    active=true;const generation=++epoch;let audio=null,questionDone=false,index=0;
    const valid=()=>active&&epoch===generation;
    const fail=()=>{if(!valid())return;stop();onStatus('질문·답변 재생이 중단됐어요. 메뉴를 다시 열고 재생하거나 답변 원음만 듣기를 선택해주세요.');};
    const deadline=ms=>{clear();timer=env.setTimeout(fail,ms);};
    function question(){
      if(!valid())return;
      if(index>=cues.length){stop();onStatus('질문과 답변을 모두 들었어요. 질문은 기기 음성으로 다시 읽었습니다.');return;}
      questionDone=false;utterance=new env.SpeechSynthesisUtterance(cues[index].questionText);
      utterance.lang='ko-KR';utterance.voice=voice;utterance.rate=1;
      utterance.onend=()=>{if(!valid())return;questionDone=true;clear();answer();};utterance.onerror=fail;
      onStatus(`${index+1}번째 질문 · 휴대폰/PC의 한국어 음성으로 다시 읽어요.`);deadline(30000);
      try{synth.speak(utterance);}catch{fail();}
    }
    function answer(){
      if(!valid()||!questionDone)return;
      if(!audio){deadline(20000);return;}
      const cue=cues[index],end=(cues[index+1]?.at??recording.duration)/1000,at=cue.at/1000;
      if(cue.skipped||end<=at){index++;question();return;}
      try{
        source=context.createBufferSource();source.buffer=audio;source.connect(context.destination);
        source.onended=()=>{if(!valid())return;clear();source.disconnect();source=null;index++;question();};
        onStatus(`${index+1}번째 답변 · 차량에서 녹음한 원음이에요.`);deadline((end-at)*1000+10000);source.start(0,at,end-at);
      }catch{fail();}
    }
    try{
      // Resume Web Audio and start the first question directly inside the user's tap.
      context=new Context();const current=context;onChange();
      Promise.all([current.resume(),Promise.resolve().then(loadAudio).then(bytes=>current.decodeAudioData(bytes))]).then(([,buffer])=>{
        if(!valid())return;if(current.state!=='running'){fail();return;}audio=buffer;if(questionDone)answer();
      }).catch(fail);
      question();
    }catch{fail();}
  }
  // Prime the device voice list; some browsers load it asynchronously on first access.
  env.speechSynthesis?.getVoices();
  return {start,stop,active:()=>active};
}
