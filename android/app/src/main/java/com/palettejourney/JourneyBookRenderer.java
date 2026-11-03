package com.palettejourney;

import android.content.Context;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Web canvas/SVG fidelity without credentials, networking, editing or a JavaScript bridge. */
final class JourneyBookRenderer {
    private static String asset(Context context,String path)throws Exception{try(java.io.InputStream in=context.getAssets().open(path)){return Base64.encodeToString(in.readAllBytes(),Base64.NO_WRAP);}}
    static String html(Context context,JSONObject page)throws Exception{
        JSONObject assets=new JSONObject();JSONArray stickers=page.optJSONArray("stickers");
        if(stickers!=null)for(int i=0;i<stickers.length();i++){
            String id=stickers.getJSONObject(i).getString("stickerId");String name;
            switch(id){case "basic-star":name="star";break;case "basic-heart":name="heart";break;case "gyeongju-cheomseongdae":name="cheomseongdae";break;case "gyeongju-silla-crown":name="silla-crown";break;default:throw new java.io.IOException("UNKNOWN_STICKER");}
            if(!assets.has(id))assets.put(id,new JSONObject().put("base",asset(context,"stickers/"+name+".svg")).put("outline",asset(context,"stickers/"+name+"-outline.svg")));
        }
        String template;try(java.io.InputStream in=context.getAssets().open("journey-book.html")){template=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        String data=new JSONObject().put("page",page).put("assets",assets).toString();return template.replace("__PAGE_DATA__",Base64.encodeToString(data.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP));
    }
}
