// Real emulator SDK inputs; isolated in-memory family data only. No microphone use.
import {spawn, execFileSync} from 'node:child_process';
import http from 'node:http';
import {existsSync, readFileSync, mkdirSync, writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomUUID} from 'node:crypto';
import {AccountStore} from '../backend/store.mjs';
import {createApp} from '../backend/server.mjs';

const root=fileURLToPath(new URL('../',import.meta.url));
const args=process.argv.slice(2);
function option(name,fallback){const index=args.indexOf(name);return index<0?fallback:args[index+1];}
const serial=option('--serial','emulator-5554'),runs=Number(option('--runs','2'));
if(!/^emulator-\d+$/.test(serial)||!Number.isInteger(runs)||runs<1||runs>5)throw Error('Use --serial emulator-5554 --runs 1..5');
const localProperties=join(root,'android/local.properties');
const localSdk=existsSync(localProperties)?readFileSync(localProperties,'utf8').match(/^sdk\.dir=(.+)$/m)?.[1].trim().replaceAll('\\:',':').replaceAll('\\\\','\\'):null;
const sdk=option('--sdk',localSdk||process.env.ANDROID_SDK_ROOT||process.env.ANDROID_HOME||join(process.env.USERPROFILE||'','AppData/Local/Android/Sdk'));
const adb=join(sdk,'platform-tools',process.platform==='win32'?'adb.exe':'adb');
if(!existsSync(adb))throw Error('ADB not found; specify --sdk');
const adbCall=(args)=>execFileSync(adb,['-s',serial,...args],{encoding:'utf8',windowsHide:true,timeout:15000});
if(adbCall(['shell','getprop','sys.boot_completed']).trim()!=='1')throw Error('Wait for the vehicle emulator to finish booting');
adbCall(['forward','tcp:8088','tcp:8088']);
const outputDir=join(root,'evidence','drive-verification');mkdirSync(outputDir,{recursive:true});

function consoleCall(path,method='PUT'){
  return new Promise((yes,no)=>{
    const request=http.request('http://127.0.0.1:8088'+path,{method,agent:false,timeout:4000},response=>{
      response.resume();response.on('end',()=>response.statusCode===200?yes():no(Error('Console HTTP '+response.statusCode)));
    });
    request.on('error',no);request.on('timeout',()=>request.destroy(Error('Console timed out')));request.end();
  });
}

async function runOnce(index){
  const id=randomUUID(),client='palette-verification-'+id,stages=[];
  let drive=false,stopping=false,pending=null,output='',buffer='';
  const beat=()=>consoleCall('/browser/session?client='+client,'POST');
  await beat();await consoleCall('/vehicle/controls?speed=0&accelerator=0&brake=0');await consoleCall('/vehicle/gear?gear=Park');
  const log=spawn(adb,['-s',serial,'logcat','-v','raw','-T','1','DriveDemoTest:I','*:S'],{windowsHide:true});
  log.on('error',error=>console.error('ADB log stream: '+error.message));
  log.stdout.setEncoding('utf8');
  log.stdout.on('data',chunk=>{
    buffer+=chunk;const lines=buffer.split('\n');buffer=lines.pop();
    for(const line of lines){
      if(!line.includes(id+':'))continue;
      const stage=line.slice(line.indexOf(id+':')+id.length+1).trim();stages.push(stage);console.log(`Run ${index}: ${stage}`);
      if(stage.startsWith('READY_FOR_CONSOLE_'))drive=true;
      else if(stage.startsWith('ARRIVED_')||stage==='COMPLETE')drive=false;
    }
  });
  const timer=setInterval(()=>{
    if(pending||stopping)return;
    pending=(async()=>{
      try{await beat();await consoleCall('/vehicle/gear?gear='+(drive?'Drive':'Park'));await consoleCall('/vehicle/controls?speed='+(drive?60:0)+'&accelerator='+(drive?35:0)+'&brake=0');}
      catch(error){console.log(`Run ${index}: console unavailable (${error.message})`);}
      finally{pending=null;}
    })();
  },650);
  const test=spawn(adb,['-s',serial,'shell','am','instrument','-w','-e','driveRunId',id,'-e','class','com.palettejourney.DevDriveDeviceTest#consoleDriveTwoDestinationsAndFamilyReset','com.palettejourney.submission.test/androidx.test.runner.AndroidJUnitRunner'],{windowsHide:true});
  test.stdout.setEncoding('utf8');test.stderr.setEncoding('utf8');
  test.stdout.on('data',chunk=>{output+=chunk;process.stdout.write(chunk);});test.stderr.on('data',chunk=>{output+=chunk;process.stderr.write(chunk);});
  const timeout=setTimeout(()=>{output+='\nVerification timed out\n';test.kill();},240000);
  const started=Date.now();
  try{
    await new Promise((yes,no)=>{test.once('error',no);test.once('exit',yes);});
    const result={run:index,id,seconds:Math.round((Date.now()-started)/1000),stages,passed:/OK \(1 test\)/.test(output)&&stages.includes('COMPLETE')};
    writeFileSync(join(outputDir,id+'.log'),output);
    writeFileSync(join(outputDir,id+'.json'),JSON.stringify(result,null,2));
    if(!result.passed)throw Error(`Run ${index} failed; see evidence/drive-verification/${id}.log`);
    return result;
  }finally{
    stopping=true;clearInterval(timer);clearTimeout(timeout);if(pending)await pending;log.kill();
    for(const [path,method] of [['/vehicle/controls?speed=0&accelerator=0&brake=0','PUT'],['/vehicle/gear?gear=Park','PUT'],['/browser/session?client='+client+'&closing=1','POST']]){
      try{await consoleCall(path,method);}catch(error){console.log('Console cleanup: '+error.message);}
    }
  }
}

const store=new AccountStore(':memory:');store.mockLocationAccess=true;store.developmentStickerAccess=false;
const server=createApp(store);
const results=[];
const summaryPath=join(outputDir,'latest.json');
writeFileSync(summaryPath,JSON.stringify({passed:false,status:'running',results},null,2));
try{
  // A busy port is an error. Never terminate or reuse the user's normal server.
  await new Promise((yes,no)=>{server.once('error',no);server.listen(3000,'127.0.0.1',yes);});
  console.log('Isolated in-memory verification server: 127.0.0.1:3000');
  for(let index=1;index<=runs;index++)results.push(await runOnce(index));
  writeFileSync(summaryPath,JSON.stringify({passed:true,status:'complete',results},null,2));
  console.log(`PASS: ${runs} complete consecutive drive/reward/reset runs`);
}catch(error){writeFileSync(summaryPath,JSON.stringify({passed:false,status:'failed',message:error.message,results},null,2));console.error(error.message);process.exitCode=1;}
finally{server.closeAllConnections();await new Promise(yes=>server.close(yes));store.close();}
