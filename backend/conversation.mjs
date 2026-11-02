import {spawn} from 'node:child_process';

// Invoke only installed System.Speech APIs. User text arrives as JSON stdin, never command text.
const SPEECH_COMMAND=String.raw`
$ErrorActionPreference='Stop'
[Console]::InputEncoding=[Text.Encoding]::UTF8
[Console]::OutputEncoding=[Text.Encoding]::UTF8
Add-Type -AssemblyName System.Speech
$request=[Console]::In.ReadToEnd() | ConvertFrom-Json
$synth=New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
 $voice=$synth.GetInstalledVoices() | Where-Object {$_.Enabled -and $_.VoiceInfo.Culture.Name -eq 'ko-KR'} | Select-Object -First 1
 if(!$voice){throw 'NO_VOICE'}
 $synth.SelectVoice($voice.VoiceInfo.Name)
 $format=New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000,[System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,[System.Speech.AudioFormat.AudioChannel]::Mono)
 $audio=@()
 foreach($text in $request.questions){
  if($text -isnot [string] -or $text.Length -gt 300){throw 'INVALID_QUESTION'}
  $stream=New-Object IO.MemoryStream
  try{$synth.SetOutputToAudioStream($stream,$format);$synth.Speak($text);$audio += [Convert]::ToBase64String($stream.ToArray())}finally{$synth.SetOutputToNull();$stream.Dispose()}
 }
 [Console]::Write((ConvertTo-Json -InputObject $audio -Compress))
}finally{$synth.Dispose()}
`;
export async function synthesizeQuestions(questions){
 if(process.platform!=='win32')throw new Error('CONVERSATION_UNAVAILABLE');
 return new Promise((resolve,reject)=>{
  const child=spawn('C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe',['-NoProfile','-NonInteractive','-Command',SPEECH_COMMAND],{windowsHide:true,stdio:['pipe','pipe','pipe']});
  let output='',length=0,settled=false;
  const finish=(error,value)=>{if(settled)return;settled=true;clearTimeout(timer);error?reject(new Error('CONVERSATION_UNAVAILABLE')):resolve(value);};
  const timer=setTimeout(()=>{child.kill();finish(true);},30000);
  child.stdout.on('data',chunk=>{length+=chunk.length;if(length>6000000){child.kill();finish(true);}else output+=chunk.toString('utf8');});
  child.stderr.on('data',()=>{});child.on('error',()=>finish(true));child.stdin.on('error',()=>finish(true));
  child.on('close',code=>{try{if(code!==0)throw new Error();const data=JSON.parse(output.replace(/^\uFEFF/,''));if(!Array.isArray(data)||data.length!==questions.length)throw new Error();const pcm=data.map(value=>{const b=Buffer.from(value,'base64');if(b.length<2||b.length>960000||b.length%2)throw new Error();return b;});finish(false,pcm);}catch{finish(true);}});
  child.stdin.end(JSON.stringify({questions}));
 });
}
function rms(bytes){let sum=0;for(let i=0;i<bytes.length;i+=2){const sample=bytes.readInt16LE(i);sum+=sample*sample;}return bytes.length?Math.sqrt(sum/(bytes.length/2)):0;}
function soften(question,answer){
 const q=rms(question),a=rms(answer),gain=Math.min(.35,q?Math.max(a,100)/q:1),out=Buffer.alloc(question.length);
 for(let i=0;i<question.length;i+=2)out.writeInt16LE(Math.round(question.readInt16LE(i)*gain),i);
 return out;
}
export function composeConversation(recording,wav,questions){
 const pcm=Buffer.from(wav).subarray(44),parts=[],timeline=[];let length=0;
 for(let i=0;i<recording.cues.length;i++){
  const cue=recording.cues[i],end=recording.cues[i+1]?.at??recording.duration;
  const from=Math.min(pcm.length,cue.at*32),to=i===recording.cues.length-1?pcm.length:Math.min(pcm.length,end*32);
  const answer=cue.skipped?Buffer.alloc(0):pcm.subarray(from,to),question=soften(questions[i],answer.length?answer:pcm);
  timeline.push({questionAt:Math.floor(length/32),answerAt:Math.floor((length+question.length+6400)/32)});
  for(const part of [question,Buffer.alloc(6400),answer,Buffer.alloc(6400)]){parts.push(part);length+=part.length;}
 }
 if(length>8000000)throw new Error('CONVERSATION_UNAVAILABLE');
 const head=Buffer.alloc(44);head.write('RIFF');head.writeUInt32LE(length+36,4);head.write('WAVEfmt ',8);head.writeUInt32LE(16,16);head.writeUInt16LE(1,20);head.writeUInt16LE(1,22);head.writeUInt32LE(16000,24);head.writeUInt32LE(32000,28);head.writeUInt16LE(2,32);head.writeUInt16LE(16,34);head.write('data',36);head.writeUInt32LE(length,40);
 return {audio:Buffer.concat([head,...parts]),timeline};
}
export class ConversationStore {
 constructor(voice,synthesize=synthesizeQuestions){this.voice=voice;this.db=voice.db;this.synthesize=synthesize;this.pending=new Map();this.queue=Promise.resolve();}
 async audio(token,id){
  const metadata=this.voice.get(token,id),revision=metadata.revision;
  if(!metadata.recording)throw new Error('VOICE_NOT_FOUND');
  if(metadata.recording.mode!=='vehicle-answer-original')return this.voice.audio(token,id);
  let row=this.db.prepare('SELECT * FROM page_conversation WHERE page_id=? AND voice_revision=? AND version=1').get(id,revision);
  if(!row){
   const key=id+':'+revision;let job=this.pending.get(key);
   if(!job){
    if(this.pending.size>=4)throw new Error('CONVERSATION_UNAVAILABLE');
    const original=Buffer.from(this.voice.audio(token,id).audio),recording=metadata.recording;
    job=this.queue.catch(()=>{}).then(async()=>{
     const questions=await this.synthesize(recording.cues.map(c=>c.questionText)),result=composeConversation(recording,original,questions);
     this.db.exec('BEGIN IMMEDIATE');try{
      const current=this.voice.get(token,id);if(current.revision!==revision)throw new Error('VOICE_CONFLICT');
      this.db.prepare('INSERT OR REPLACE INTO page_conversation VALUES (?,?,1,?)').run(id,revision,result.audio);this.db.exec('COMMIT');
     }catch(error){this.db.exec('ROLLBACK');throw error;}
    }).finally(()=>this.pending.delete(key));this.pending.set(key,job);this.queue=job;
   }
   await job;
  }
  const current=this.voice.get(token,id);if(current.revision!==revision)throw new Error('VOICE_CONFLICT');
  row=this.db.prepare('SELECT audio FROM page_conversation WHERE page_id=? AND voice_revision=?').get(id,revision);
  return {revision,mime:'audio/wav',audio:row.audio};
 }
}
