// Ephemeral session commands: restart, session change and lost heartbeat all stop control.
export class AmbientStore {
  constructor(accounts){this.accounts=accounts;this.commands=new Map();}
  session(token){return this.accounts.participantSession(token);}
  get(token){
    const session=this.session(token),s=this.commands.get(session.id),now=this.accounts.now();
    if(!s)return {enabled:false,revision:0,color:null,status:'off'};
    const enabled=s.enabled&&now-s.updatedAt<10000;
    return {enabled,revision:s.revision,color:s.color,status:!enabled?'off':s.ack&&now-s.ack.at<5000?s.ack.status:'waiting',actualHex:enabled?s.ack?.actualHex??null:null};
  }
  set(token,body){
    const session=this.session(token);
    if(!body||typeof body.enabled!=='boolean')throw Error('INVALID_LIGHT');
    const color=body.enabled?this.accounts.sessionPalette(token).colors.find(c=>c.id===body.colorId):null;
    if(body.enabled&&!color)throw Error('INVALID_COLOR');
    let s=this.commands.get(session.id);
    const changed=!s||s.enabled!==body.enabled||s.color?.id!==color?.id||this.accounts.now()-s.updatedAt>=10000;
    if(changed)s={enabled:body.enabled,revision:(s?.revision??0)+1,color:color?{id:color.id,hex:color.hex,name:color.name??color.id}:null,ack:null};
    s.updatedAt=this.accounts.now();this.commands.set(session.id,s);
    return this.get(token);
  }
  ack(token,body){
    const session=this.session(token),s=this.commands.get(session.id);
    if(!s||!this.get(token).enabled||body?.revision!==s.revision)throw Error('LIGHT_CONFLICT');
    if(!['applied','blocked','unsupported','error'].includes(body.status))throw Error('INVALID_LIGHT');
    if(body.status==='applied'&&body.actualHex!==s.color.hex)throw Error('INVALID_LIGHT');
    s.ack={status:body.status,actualHex:body.status==='applied'?body.actualHex:null,at:this.accounts.now()};
    return this.get(token);
  }
}
