export class ConnectionStore {
 constructor(accounts,diaries){this.accounts=accounts;this.diaries=diaries;}
 get(token){const session=this.accounts.participantSession(token),library=this.diaries.library(token);
 const focus=this.accounts.db.prepare('SELECT * FROM session_focus WHERE session_id=?').get(session.id);
 return {webOrigin:process.env.PALETTE_WEB_ORIGIN||'http://localhost:3000',sessionId:session.id,expiresAt:session.expires,familyId:library.familyId,revision:focus?.revision??0,page:library.pages.find(p=>p.id===focus?.page_id)??null};}
 set(token,body){if(!body||!Number.isSafeInteger(body.revision)||typeof body.pageId!=='string')throw new Error('INVALID_DIARY');
 this.diaries.page(token,body.pageId);const current=this.get(token),db=this.accounts.db;
 if(body.revision!==current.revision)throw new Error('CONNECTION_CONFLICT');
 db.prepare('INSERT OR IGNORE INTO session_focus VALUES (?,NULL,0)').run(current.sessionId);
 const result=db.prepare('UPDATE session_focus SET page_id=?,revision=revision+1 WHERE session_id=? AND revision=?').run(body.pageId,current.sessionId,body.revision);
 if(!result.changes)throw new Error('CONNECTION_CONFLICT');return this.get(token);}
}
